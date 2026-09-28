"""Anchor rendered shoe soles to observed ground, retaining GVHMR body scale."""
import cv2
import numpy as np
from scipy.ndimage import median_filter,grey_closing
from scipy.sparse import coo_matrix,diags,eye
from scipy.sparse.linalg import spsolve
from source.enhancement import anatomical


def observe(paths,poses,geometry,*,alpha=False):
    """Left/right sole pixels, not the model's barefoot toe-joint projection."""
    n=len(paths);feet=np.full((n,2,2),np.nan);x_valid=np.zeros((n,2),bool)
    for i,path in enumerate(paths):
        image=cv2.imread(str(path),-1 if alpha else 0)
        mask=image[:,:,3] if alpha else image;h,w=mask.shape
        height=float(geometry['height']*geometry['unit_scale'][i])
        radius=max(4.,height*.075)
        centers=geometry['feet'][i].copy()
        for j,k in enumerate((13,10)):  # DWPose L/R; GVHMR is also L/R.
            p=poses[i,k]
            if p[2]>.45 and np.isfinite(p).all() and np.linalg.norm(p[:2]-centers[j])<height*.2:
                centers[j]=p[:2]
        separated=np.linalg.norm(centers[0]-centers[1])>radius*.6
        for j,(x,y) in enumerate(centers):
            if not np.isfinite([x,y]).all() or not (0<=x<w and 0<=y<h):continue
            x0=max(0,int(x-radius));x1=min(w,int(x+radius)+1)
            y0=max(0,int(y-height*.035));y1=min(h,int(y+height*.16)+1)
            yy,xx=np.where(mask[y0:y1,x0:x1]>200);xx=xx+x0;yy=yy+y0
            if separated:
                own=(xx-x)**2+(yy-y)**2;other=((np.column_stack([xx,yy])-centers[1-j])**2).sum(1)
                xx,yy=xx[own<=other],yy[own<=other]
            if len(yy)<8:continue
            bottom=float(np.quantile(yy,.99))
            if bottom>=h-2 or bottom>=y1-2 or bottom<y-height*.03:continue
            band=yy>=bottom-max(2.,height*.006)
            feet[i,j]=[float(np.median(xx[band])),bottom]
            x_valid[i,j]=separated and geometry['confidence'][i]>.35
    # Remove isolated mask spikes without lagging a moving foot.
    for j in range(2):
        for k in range(2):
            valid=np.isfinite(feet[:,j,k])
            if valid.any():feet[valid,j,k]=median_filter(anatomical.fill(feet[:,j,k]),size=3,mode='nearest')[valid]
    return feet,x_valid


def ground(feet,geometry,fps,*,camera=None,origin=(0.,0.),axis=1):
    """Reconstruct each foot's floor independently, then use their fixed mean.

    Changing support feet must not move the body by the projected height
    difference between a near and a far foot. Missing samples follow camera
    motion and the same foot's landings, never an airborne root projection.
    """
    n=len(feet);origin=np.asarray(origin)
    if camera is None:camera=dict(matrix=np.repeat(np.eye(3)[None],n,axis=0),valid=np.ones(n,bool))
    inverse=np.linalg.inv(camera['matrix'])
    points=np.einsum('nij,nkj->nki',inverse[:,:2,:2],feet+origin)+inverse[:,:2,2][:,None]
    visible=np.isfinite(points).all(2)&(geometry['confidence'][:,None]>.35)&camera['valid'][:,None]
    unit=geometry['height']*geometry['unit_scale']/np.sqrt(np.linalg.det(camera['matrix'][:,:2,:2]))
    _,stationary=stationary_plants(feet,visible,geometry,camera,origin,fps)
    floors=np.full((n,2),np.nan);observed=np.zeros((n,2),bool)
    for j in range(2):
        y=points[:,j,1];filled=anatomical.fill(np.where(visible[:,j],y,np.nan))
        # Close short upward excursions to reject a lifted foot falsely labelled
        # as contact. A sustained change of stage depth can still supply samples.
        envelope=grey_closing(median_filter(filled,size=3,mode='nearest'),
            size=max(3,round(fps))|1,mode='nearest')
        good=visible[:,j]&(stationary[:,j]|(geometry['contact'][:,j]>=.25))&(y>=envelope-unit*.035)
        if not good.any() or (axis==0 and good.sum()<max(3,round(fps*.15))):continue
        line=anatomical.fit_curve(anatomical.fill(np.where(good,y,np.nan)),fps,seconds=.08)
        x=anatomical.fit_curve(anatomical.fill(np.where(good,points[:,j,0],np.nan)),fps,seconds=.16 if axis==0 else .08)
        floors[:,j]=(camera['matrix'][:,axis,0]*x+camera['matrix'][:,axis,1]*line+
                     camera['matrix'][:,axis,2]-origin[axis])
        observed[:,j]=good
    support=np.full(n,-1,int);known=observed.any(1)
    support[known]=np.where(observed[known],feet[known,:,1],-np.inf).argmax(1)
    available=np.isfinite(floors).any(0)
    if axis==0 and not available.all():return geometry['anchor'][:,0].copy(),np.full(n,-1,int)
    if not available.any():return geometry['anchor'][:,axis].copy(),support
    # A foot with missing samples stays in the mean via its reconstructed path;
    # changing confidence must not change the reference point or its weights.
    return anatomical.fit_curve(floors[:,available].mean(1),fps,seconds=.16 if axis==0 else .08),support


def ankle_reference(feet,poses,geometry):
    """A fixed shoe-to-ankle offset keeps a rotating silhouette out of placement."""
    reference=np.full_like(feet,np.nan)
    if poses is None:return reference
    height=geometry['height']*geometry['unit_scale']
    for j,k in enumerate((13,10)):
        ankle=poses[:,k]
        good=(np.isfinite(feet[:,j]).all(1)&np.isfinite(ankle).all(1)&(ankle[:,2]>.45)
              &(np.linalg.norm(feet[:,j]-ankle[:,:2],axis=1)<height*.12))
        if not good.any():continue
        offset=np.median(feet[good,j,0]-ankle[good,0])
        reference[good,j]=feet[good,j]
        reference[good,j,0]=ankle[good,0]+offset
    return reference


def intervals(support):
    edges=np.r_[0,np.flatnonzero(support[1:]!=support[:-1])+1,len(support)]
    return [(int(a),int(b)) for a,b in zip(edges[:-1],edges[1:]) if support[a]>=0]


def stationary_plants(feet,valid,geometry,camera,origin,fps,*,horizontal=False,ankles=None):
    """Keep a plant through weak model labels while the observed shoe stays put."""
    n=len(feet);inverse=np.linalg.inv(camera['matrix'])
    points=np.einsum('nij,nkj->nki',inverse[:,:2,:2],feet+origin)+inverse[:,:2,2][:,None]
    unit=geometry['height']*geometry['unit_scale']/np.sqrt(np.linalg.det(camera['matrix'][:,:2,:2]))
    plants=[];samples=np.zeros((n,2),bool);minimum=max(2,round(fps*.12))
    for j in range(2):
        visible=valid[:,j]&np.isfinite(points[:,j]).all(1)&(geometry['confidence']>.35)&camera['valid']
        if horizontal and ankles is not None:
            # Shoe/hem movement relative to the ankle is a pivot or changing
            # silhouette, not whole-person sliding. Camera translation cancels.
            shape=(feet[:,j]-ankles[:,j,:2])/(geometry['height']*geometry['unit_scale'][:,None])
            visible&=(ankles[:,j,2]>.45)&np.isfinite(shape).all(1)&(np.linalg.norm(shape,axis=1)<.12)
        filled=np.column_stack([anatomical.fill(np.where(visible,points[:,j,k],np.nan)) for k in range(2)])
        speed=np.linalg.norm(np.gradient(filled,axis=0),axis=1)*fps/unit if n>1 else np.zeros(n)
        stable=visible&(median_filter(speed,size=3,mode='nearest')<.14)
        current=[]
        for a,b in intervals(np.where(stable,j,-1)):
            if b-a<minimum or not np.any(geometry['contact'][a:b,j]>=.55):continue
            # A slow pivot/slide can pass the per-frame speed test. Horizontal
            # locking also needs the same small footprint over the whole plant.
            if horizontal and np.linalg.norm(np.ptp(filled[a:b],axis=0))>np.median(unit[a:b])*.02:continue
            # A nearly unchanged shoe needs no ankle veto: noisy ankle estimates
            # alone must not discard a visibly stationary contact.
            raw_span=np.linalg.norm(np.ptp(feet[a:b,j],axis=0)) if horizontal else 0.
            if (horizontal and ankles is not None and raw_span>np.median(unit[a:b])*.025
                    and np.linalg.norm(np.ptp(shape[a:b],axis=0))>.02):continue
            samples[a:b,j]=True
            # A crossing leg can briefly hide the same stationary shoe.
            # Keep its ground point across that gap, without constraining
            # the ambiguous/missing pixels inside the gap.
            if (current and a-current[-1][1]<=max(1,round(fps*.3))
                    and np.linalg.norm(filled[a]-filled[current[-1][1]-1])<unit[a]*.012
                    and (not horizontal or np.linalg.norm(np.ptp(
                        filled[current[-1][0]:b],axis=0))<np.median(unit[current[-1][0]:b])*.02)
                    and (not horizontal or ankles is None or
                        np.linalg.norm(np.ptp(feet[current[-1][0]:b,j],axis=0))<np.median(unit[current[-1][0]:b])*.025
                        or np.linalg.norm(np.ptp(shape[current[-1][0]:b],axis=0))<.02)):
                current[-1]=(current[-1][0],b,j)
            else:current.append((a,b,j))
        plants.extend(current)
    return plants,samples


def pin_contacts(translation,scale,feet,plants,samples,camera,fps):
    """Fix the support point in scene coordinates; only free gaps are smoothed.

    One foot fixes its contact point. With both feet planted, their common
    translation is fixed; independent shoe/pose deformation is left intact.
    """
    n=len(translation);inverse=np.linalg.inv(camera['matrix'])
    world=np.einsum('nij,nkj->nki',inverse[:,:2,:2],
        feet*scale[:,None,None]+translation[:,None])+inverse[:,:2,2][:,None]
    visible=samples&np.isfinite(world).all(2)
    observed=visible.copy()
    for a,b,j in plants:
        indices=np.flatnonzero(observed[a:b,j])+a
        if len(indices)<2:continue
        # Retain the same anchor across a short validated observation gap.
        # Interpolate only the constraint; never replace the rendered foot pixels.
        for k in range(2):world[a:b,j,k]=np.interp(np.arange(a,b),indices,world[indices,j,k])
        visible[a:b,j]=True
    common=visible[1:]&visible[:-1];linked=common.any(1)
    stats=dict(plants=[dict(start=a,end=b,foot=j) for a,b,j in plants],
        planted_frames=int(observed.any(1).sum()),held_gap_frames=int((visible.any(1)&~observed.any(1)).sum()),
        single_foot_edges=int((common.sum(1)==1).sum()),double_foot_edges=int((common.sum(1)==2).sum()),
        motion_reference='camera',constraint='fixed_support_point',correction_max_px=0.)
    if not linked.any():return translation.copy(),stats
    relative=np.zeros((n,2));velocity=np.zeros((n-1,2))
    for i in range(1,n):
        if linked[i-1]:
            velocity[i-1]=np.mean(world[i,common[i-1]]-world[i-1,common[i-1]],axis=0)
            relative[i]=relative[i-1]-velocity[i-1]
    # Eliminate contact equalities instead of weakening them with weights/caps.
    # Each connected contact interval has one anchor; free frames have their own.
    groups=np.r_[0,np.cumsum(~linked)];count=int(groups[-1])+1
    projection=coo_matrix((np.ones(n),(np.arange(n),groups)),shape=(n,count)).tocsr()
    first=diags([-np.ones(n-1),np.ones(n-1)],[0,1],shape=(n-1,n))
    loss=eye(n)+(fps*.12)**2*(first.T@first)
    if n>=3:
        second=diags([np.ones(n-2),-2*np.ones(n-2),np.ones(n-2)],[0,1,2],shape=(n-2,n))
        loss=loss+(fps*.12)**4*(second.T@second)
    free=projection[:,1:]
    fit=(spsolve((free.T@loss@free).tocsc(),-free.T@loss@relative)
         if count>1 else np.zeros((0,2)))
    # The first placement is retained; corrections flow through unconstrained gaps.
    correction=projection@np.vstack([np.zeros((1,2)),np.asarray(fit).reshape(-1,2)])+relative
    delta=np.einsum('nij,nj->ni',camera['matrix'][:,:2,:2],correction)
    residual=velocity[linked]+np.diff(correction,axis=0)[linked]
    stats.update(correction_max_px=float(np.max(abs(delta[:,0]))),
        translation_correction_max_px=np.max(abs(delta),axis=0).tolist(),
        foot_speed_before_px_per_frame=float(np.mean(abs(velocity[linked,0]))),
        foot_speed_after_px_per_frame=float(np.mean(abs(residual[:,0]))),
        support_residual_max_px=float(np.max(abs(residual))))
    return translation+delta,stats


def refine(frames,background,foreground,bg_feet,fg_feet,bg_x_valid,fg_x_valid,fps,*,mirror=False,
           background_camera=None,foreground_camera=None,foreground_origin=(0.,0.),foreground_poses=None,
           background_poses=None,ground_horizontal=False,background_reference=None):
    """Use A for initial placement, then pin C contacts in the background scene."""
    n=len(frames);scale=np.array([t['scale'] for t in frames])
    old=np.array([[t['x'],t['y']] for t in frames]);new=old.copy()
    identity=dict(matrix=np.repeat(np.eye(3)[None],n,axis=0),valid=np.ones(n,bool))
    background_camera=identity if background_camera is None else background_camera
    foreground_camera=identity if foreground_camera is None else foreground_camera
    by,bs=(ground(bg_feet,background,fps,camera=background_camera) if background_reference is None
           else (background_reference['ground_y'],background_reference['support_y']))
    fy,fs=ground(fg_feet,foreground,fps,camera=foreground_camera,origin=foreground_origin)
    has_ground=bool((bs>=0).any() and (fs>=0).any())
    if has_ground:new[:,1]=by-scale*fy
    horizontal_reference=False
    if has_ground and ground_horizontal:
        # Match the landing footprint, not the phase of A/C's pelvis sway.
        # Each foot has a fixed contribution even when support switches sides.
        bx,bxs=(ground(ankle_reference(bg_feet,background_poses,background),background,fps,
            camera=background_camera,axis=0) if background_reference is None else
            (background_reference['ground_x'],background_reference['support_x']))
        fx,fxs=ground(ankle_reference(fg_feet,foreground_poses,foreground),foreground,fps,
            camera=foreground_camera,origin=foreground_origin,axis=0)
        horizontal_reference=bool((bxs>=0).any() and (fxs>=0).any())
        if horizontal_reference:new[:,0]=bx-scale*fx
    reference_adjustment=float(np.max(abs(new[:,0]-old[:,0])))
    # A failed BACKGROUND camera fit says nothing about a shoe in the C video.
    valid=fg_x_valid
    plants,samples=(stationary_plants(fg_feet,valid,foreground,foreground_camera,np.array(foreground_origin),fps,
        horizontal=True,ankles=None if foreground_poses is None else foreground_poses[:,[13,10]])
        if has_ground else ([],np.zeros((n,2),bool)))
    horizontal_stats=dict(plants=[],planted_frames=0)
    if plants:new,horizontal_stats=pin_contacts(new,scale,fg_feet,plants,samples,background_camera,fps)
    horizontal_stats.update(ground_reference_applied=horizontal_reference,
        ground_reference_adjustment_max_px=reference_adjustment)
    locked=sum(b-a-1 for a,b,j in plants)
    result=[dict(t,x=float(x),y=float(y)) for t,(x,y) in zip(frames,new)]
    visible=fs>=0
    sole=fy[visible]*scale[visible]
    before=sole+old[visible,1]-by[visible];after=sole+new[visible,1]-by[visible]
    stats=dict(ground_applied=has_ground,ground_reference='per-foot-camera-stabilized',background_contact_frames=int((bs>=0).sum()),
        foreground_contact_frames=int(visible.sum()),contact_locked_intervals=locked,horizontal=horizontal_stats,
        ground_gap_before_median_px=float(np.median(abs(before))) if len(before) else None,
        ground_gap_after_median_px=float(np.median(abs(after))) if len(after) else None,
        translation_adjustment_max_px=np.max(abs(new-old),axis=0).tolist(),
        background_ground_y=by.tolist(),foreground_ground_y=fy.tolist(),
        background_support_foot=bs.tolist(),foreground_support_foot=fs.tolist())
    return result,stats
