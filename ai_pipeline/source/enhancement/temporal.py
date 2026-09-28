"""Bounded time warping and motion-compensated RGBA interpolation (CPU)."""
import cv2
import numpy as np
from scipy.optimize import minimize, Bounds, LinearConstraint


def source_indices(times, source_times):
    """Map output seconds to fractional indices of real, timestamped source frames."""
    source_times=np.asarray(source_times,dtype=float);times=np.asarray(times,dtype=float)
    if (source_times.ndim!=1 or not len(source_times) or not np.isfinite(source_times).all()
            or np.any(np.diff(source_times)<=0) or not np.isfinite(times).all()):
        raise ValueError('유효한 원본 프레임 시각 필요')
    return np.interp(times,source_times,np.arange(len(source_times),dtype=float))


def smooth_time(mapping,fps):
    """Keep endpoints; speed 0.8–1.2x, acceleration <=1.0x/sec, shift <=0.6s."""
    n=len(mapping);identity=np.arange(n,dtype=float)
    if n<3:return identity
    delta=np.asarray(mapping,dtype=float)-identity
    radius=max(1,round(fps*.2))
    kernel=np.exp(-.5*(np.arange(-radius,radius+1)/max(1,radius/2))**2);kernel/=kernel.sum()
    delta=np.convolve(np.pad(delta,(radius,radius),mode='edge'),kernel,mode='valid')
    # Fit the path under per-interval bounds; do not shrink every interval
    # because one segment needs a large adjustment.
    low=np.full(n,-.6*fps);high=-low
    low[[0,-1]]=0;high[[0,-1]]=0
    differences=np.diff(np.eye(n),axis=0)
    acceleration=np.diff(differences,axis=0)
    constraints=[LinearConstraint(differences,-.2,.2),
                 LinearConstraint(acceleration,-1/fps,1/fps)]
    target=delta.copy()
    result=minimize(lambda x:.5*np.sum((x-target)**2),np.zeros(n),
                    jac=lambda x:x-target,method='SLSQP',bounds=Bounds(low,high),
                    constraints=constraints,options={'ftol':1e-9,'maxiter':300})
    delta=result.x
    error=max(float(np.max(np.maximum(low-delta,delta-high))),
              float(np.max(abs(np.diff(delta)))-.2),
              float(np.max(abs(np.diff(delta,n=2)))-1/fps))
    if not result.success or error>1e-7:
        raise RuntimeError('시간 경로 제약 계산 실패: '+result.message)
    delta[[0,-1]]=0
    return identity+delta


def sample_pose(poses,times):
    times=np.clip(times,0,len(poses)-1)
    # Equal FPS arithmetic can produce 31.999999999999996. It must not mix
    # frame 31's missing-joint confidence into the exact frame 32 observation.
    times=np.where(abs(times-np.rint(times))<1e-6,np.rint(times),times)
    lo=np.floor(times).astype(int);hi=np.minimum(lo+1,len(poses)-1)
    weight=(times-lo)[:,None,None]
    out=poses[lo]*(1-weight)+poses[hi]*weight
    out[:,:,2]=np.where(weight[:,:,0]>1e-6,np.minimum(poses[lo,:,2],poses[hi,:,2]),poses[lo,:,2])
    return out


def premultiply(im):
    alpha=im[:,:,3:4].astype(np.float32)/255
    return np.concatenate([im[:,:,:3]*alpha,alpha],axis=2)


def unpremultiply(im):
    rgb=np.divide(im[:,:,:3],im[:,:,3:4],out=np.zeros_like(im[:,:,:3]),where=im[:,:,3:4]>1e-6)
    return np.dstack([rgb,im[:,:,3]*255]).clip(0,255).round().astype(np.uint8)


class FrameSampler:
    """Caches one adjacent pair; invisible RGB never affects motion or color."""
    def __init__(self,paths):
        self.paths=paths;self.index=None

    def read(self,i):
        im=cv2.imread(str(self.paths[i]),cv2.IMREAD_UNCHANGED)
        if im is None or im.ndim!=3 or im.shape[2]!=4:raise ValueError('RGBA 프레임 필요')
        return im

    def sample(self,time,coordinate_map=None):
        time=float(np.clip(time,0,len(self.paths)-1))
        if abs(time-round(time))<1e-6:time=float(round(time))
        i=int(np.floor(time));t=time-i
        nearest=i if t<.5 else min(i+1,len(self.paths)-1)
        original=self.read(nearest)
        h,w=original.shape[:2];y,x=np.mgrid[:h,:w].astype(np.float32)
        grid=np.dstack([x,y])
        joint=grid if coordinate_map is None else coordinate_map
        if joint.shape!=grid.shape:raise ValueError('관절 변형 좌표 크기 불일치')
        if t<1e-6 or i==len(self.paths)-1:
            if np.array_equal(joint,grid):return original
            return unpremultiply(cv2.remap(premultiply(original),joint[:,:,0],joint[:,:,1],
                                          cv2.INTER_CUBIC,borderMode=cv2.BORDER_CONSTANT))
        if self.index!=i:
            a=self.read(i);b=self.read(i+1)
            if a.shape!=b.shape:raise ValueError('전경 프레임 크기 불일치')
            def gray(im):
                pm=premultiply(im);rgb=pm[:,:,:3]+127*(1-pm[:,:,3:4])
                return cv2.cvtColor(rgb.clip(0,255).astype(np.uint8),cv2.COLOR_BGR2GRAY)
            ga,gb=gray(a),gray(b)
            args=(None,.5,4,25,4,7,1.5,0)
            self.forward=cv2.calcOpticalFlowFarneback(ga,gb,*args)
            self.backward=cv2.calcOpticalFlowFarneback(gb,ga,*args)
            self.gray_a,self.gray_b=ga.astype(np.float32),gb.astype(np.float32)
            self.index=i
        def remap(im,xy):
            return cv2.remap(im,xy[:,:,0],xy[:,:,1],cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT)
        if nearest==i:
            flow,reverse,fraction=self.forward,self.backward,t
            gray,other=self.gray_a,self.gray_b
        else:
            flow,reverse,fraction=self.backward,self.forward,1-t
            gray,other=self.gray_b,self.gray_a
        # Compose final -> joint-corrected time -> original source coordinates.
        # Only flow/coordinate fields are sampled here; RGB is sampled once below.
        xy=joint.copy()
        for _ in range(3):xy=joint-fraction*remap(flow,xy)
        motion=remap(flow,xy);other_xy=xy+motion
        error=np.linalg.norm(motion+remap(reverse,other_xy),axis=2)
        photo=abs(remap(gray,xy)-remap(other,other_xy))
        confidence=np.clip((3-error)/2,0,1)*np.clip((40-photo)/25,0,1)
        valid=(xy[:,:,0]>=0)&(xy[:,:,0]<=w-1)&(xy[:,:,1]>=0)&(xy[:,:,1]<=h-1)
        valid &= (other_xy[:,:,0]>=0)&(other_xy[:,:,0]<=w-1)&(other_xy[:,:,1]>=0)&(other_xy[:,:,1]<=h-1)
        confidence*=valid
        # Flow confidence governs only the temporal displacement. Applying it to
        # the joint map tears the silhouette where neighboring confidences differ.
        xy=joint+confidence[:,:,None]*(xy-joint)
        result=unpremultiply(cv2.remap(premultiply(original),xy[:,:,0],xy[:,:,1],cv2.INTER_CUBIC,
                                      borderMode=cv2.BORDER_CONSTANT))
        # With unreliable flow, retain the joint-corrected nearer source. RGB and
        # alpha still share one remap; no frame blending or interior blur is added.
        return result
