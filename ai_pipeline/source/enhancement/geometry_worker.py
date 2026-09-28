"""GVHMR adapter: selected SAM identity, fixed shape, original pixel coordinates."""
import os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'vendor/GVHMR')]
os.environ['MPLBACKEND']='Agg'
import numpy as np
from source.util import removal_experiments as ex
from source.enhancement.geometry_placement import sample,validate_track


def projected_track(camera,world,rotation,K,height,contact,confidence):
    """Project the scene ground beneath the body; pitching does not change stature."""
    n=len(camera)
    if camera.shape!=(n,22,3) or world.shape!=camera.shape:raise ValueError('GVHMR 22관절 출력 필요')
    if not np.isfinite(camera).all() or np.any(camera[:,:,2]<=.01):raise ValueError('GVHMR 카메라 뒤의 신체 좌표')
    # GVHMR global coordinates use upward +Y. Its static labels are L/R ankle/toe.
    feet_world=world[:,[10,11]]
    planted=contact>.75
    ground=float(np.quantile(feet_world[:,:,1][planted],.1) if planted.any()
                 else np.quantile(feet_world[:,:,1],.05))
    anchor_world=world[:,0].copy();anchor_world[:,1]=ground
    camera_translation=camera[:,0]-np.einsum('nij,nj->ni',rotation,world[:,0])
    anchor_camera=np.einsum('nij,nj->ni',rotation,anchor_world)+camera_translation
    def project(points):
        z=points[...,2:3]
        if np.any(z<=.01):raise ValueError('바닥 투영 좌표가 카메라 뒤에 있습니다.')
        normalized=points/z
        return np.einsum('nij,n...j->n...i',K,normalized)[...,:2]
    result=dict(unit_scale=K[:,0,0]/camera[:,0,2],anchor=project(anchor_camera),
        feet=project(camera[:,[10,11]]),height=np.array(height),contact=contact,confidence=confidence)
    validate_track(result,n);return result


def main(path):
    # Native inference dependencies are loaded only inside the isolated worker.
    # gcsfuse mmap 로딩 병목 회피: GVHMR ckpt·ViTPose-H(~2.4G)·DPVO의 torch.load를 순차-read로. import보다 먼저.
    from source.util import seq_load;seq_load.install('GVHMR')
    import torch,hydra
    from hydra import initialize_config_module,compose
    from pytorch3d.transforms import axis_angle_to_matrix
    from hmr4d.configs import register_store_gvhmr
    from hmr4d.model.gvhmr.gvhmr_pl_demo import DemoPL
    from hmr4d.utils.preproc import Extractor,VitPoseExtractor,SimpleVO
    from hmr4d.utils.geo.hmr_cam import get_bbx_xys_from_xyxy,estimate_K
    from hmr4d.utils.geo_transform import compute_cam_angvel
    cfg=ex.read_json(ex.local(path,exists=True));work=ex.local(cfg['work'],exists=True)
    stage=ex.local(cfg['stage'],exists=True)
    if not torch.cuda.is_available():raise RuntimeError('GVHMR 분석에는 CUDA GPU가 필요합니다.')
    torch.set_num_threads(8)
    obs=np.load(stage/'observations.npz',allow_pickle=False)
    boxes=torch.from_numpy(obs['boxes']).float();n=len(boxes)
    bbx=get_bbx_xys_from_xyxy(boxes,base_enlarge=1.2).float()
    person=str(ex.local(stage/'person.mp4',exists=True));scene=str(ex.local(stage/'scene.mp4',exists=True))
    with torch.inference_mode():
        detector=VitPoseExtractor();kp=detector.extract(person,bbx,img_ds=1.)
        del detector;torch.cuda.empty_cache()
        extractor=Extractor();features=extractor.extract_video_features(person,bbx,img_ds=1.)
        del extractor;torch.cuda.empty_cache()
        if len(kp)!=n or len(features)!=n:raise ValueError('GVHMR 전처리 프레임 수 불일치')
        K=estimate_K(*cfg['size']).repeat(n,1,1)
        if n>=16:
            trajectory=SimpleVO(scene,scale=.5,step=8,method='sift',f_mm=None).compute()
            rotations=torch.from_numpy(trajectory[:,:3,:3]).float()
        else:
            rotations=torch.eye(3).repeat(n,1,1)
        if len(rotations)!=n or not torch.isfinite(rotations).all():raise ValueError('카메라 회전 추정 실패')
        register_store_gvhmr()
        with initialize_config_module(version_base='1.3',config_module='hmr4d.configs'):
            config=compose(config_name='demo',overrides=['video_name=placement'])
        model: DemoPL=hydra.utils.instantiate(config.model,_recursive_=False)
        model.load_pretrained_model(ex.local('vendor/GVHMR/inputs/checkpoints/gvhmr/gvhmr_siga24_release.ckpt'))
        model=model.eval().cuda()
        data=dict(length=torch.tensor(n),bbx_xys=bbx,kp2d=kp,K_fullimg=K,
                  cam_angvel=compute_cam_angvel(rotations),f_imgseq=features)
        prediction=model.predict(data,static_cam=False)
        incam=prediction['smpl_params_incam'];global_pose=prediction['smpl_params_global']
        # One shape per original person; per-frame beta noise cannot resize the body.
        beta=incam['betas'].median(dim=0).values
        incam['betas']=beta[None].expand(n,-1);global_pose['betas']=incam['betas']
        decoder=model.pipeline.endecoder
        camera=decoder.fk_v2(**{k:v[None] for k,v in incam.items()})[0]
        world=decoder.fk_v2(**{k:v[None] for k,v in global_pose.items()})[0]
        rotation=axis_angle_to_matrix(incam['global_orient'])@axis_angle_to_matrix(global_pose['global_orient']).mT
        # Fixed neutral-pose stature, including the skull/sole extents of SMPL-X.
        body=np.load(ex.local('vendor/GVHMR/inputs/checkpoints/body_models/smplx/SMPLX_NEUTRAL.npz'),allow_pickle=False)
        vertices=body['v_template']+np.einsum('vck,k->vc',body['shapedirs'][:,:,:10],beta.cpu().numpy())
        height=float(np.ptp(vertices[:,1]));body.close()
        logits=prediction['net_outputs']['static_conf_logits'][0].sigmoid().cpu().numpy()
        contact=np.stack([logits[:,[0,1]].min(1),logits[:,[2,3]].min(1)],1)
        # Compare projected 3D anchors with the measured 2D pose; distrust bad fits.
        ids=[16,17,1,2,4,5,7,8];coco=[5,6,11,12,13,14,15,16]
        selected=camera[:,ids];projected=selected[:,:,:2]/selected[:,:,2:3]
        projected=projected*K[:,None,:2,:2].diagonal(dim1=-2,dim2=-1).to(projected.device)+K[:,None,:2,2].to(projected.device)
        measured=kp[:,coco].to(projected.device)
        error=torch.linalg.norm(projected-measured[:,:,:2],dim=2)/bbx[:,None,2].to(projected.device)
        known=measured[:,:,2]>.5
        residual=torch.where(known,error,torch.nan).nanmedian(dim=1).values.nan_to_num(nan=1.)
        confidence=(known.float().mean(1)*torch.exp(-residual/.1)).cpu().numpy()*obs['observed']
        track=projected_track(camera.cpu().numpy(),world.cpu().numpy(),rotation.cpu().numpy(),K.numpy(),height,contact,confidence)
        # Model analysis uses 30 FPS; the caller/rendering retains original frame times.
        times=np.arange(cfg['frames'])*30/cfg['fps']
        track={key:value if value.ndim==0 else sample(value,times) for key,value in track.items()}
        validate_track(track,cfg['frames'])
        target=work/'geometry.partial.npz';np.savez_compressed(target,**track);target.replace(work/'geometry.npz')
        print('GVHMR 원본 기하 저장 완료:',cfg['frames'],'프레임 · 원본 FPS',cfg['fps'],flush=True)


if __name__=='__main__':main(sys.argv[1])
