"""Isolated Video Depth Anything Large inference; no FPS resampling."""
import os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'.model-envs/video-depth-deps'),str(ROOT/'vendor/Video-Depth-Anything')]
os.environ['MPLBACKEND']='Agg'
import cv2,numpy as np,torch
from source.util import removal_experiments as ex
from source.util import seq_load


def temporal_attention(self,q,k,v,attention_mask=None):
    # Spatial positions and heads have been folded into the batch dimension.
    # Large portrait frames can exceed CUDA SDPA's 65535 batch/grid limit.
    # Split independent batch rows; retain the complete temporal sequence.
    out=torch.empty((*q.shape[:-1],v.shape[-1]),device=q.device,dtype=v.dtype)
    for start in range(0,q.shape[0],8192):
        end=start+8192
        mask=attention_mask
        if mask is not None:
            if mask.shape[0]!=1:mask=mask[start:end]
            mask=mask.unsqueeze(1)
        out[start:end]=torch.nn.functional.scaled_dot_product_attention(
            q[start:end].unsqueeze(1),k[start:end].unsqueeze(1),v[start:end].unsqueeze(1),
            attn_mask=mask).squeeze(1)
    return self.reshape_batch_dim_to_heads(out)


def load_model(checkpoint):
    from video_depth_anything.video_depth import VideoDepthAnything
    from video_depth_anything.dinov2_layers import attention
    from video_depth_anything.motion_module import attention as temporal, motion_module
    # Native SDPA avoids an xFormers/PyTorch ABI dependency and large QK matrices.
    def spatial(self,x,attn_bias=None):
        b,n,c=x.shape
        q,k,v=self.qkv(x).reshape(b,n,3,self.num_heads,c//self.num_heads).permute(2,0,3,1,4).unbind(0)
        out=torch.nn.functional.scaled_dot_product_attention(q,k,v,attn_mask=attn_bias,
            dropout_p=self.attn_drop.p if self.training else 0.)
        return self.proj_drop(self.proj(out.transpose(1,2).reshape(b,n,c)))
    attention.Attention.forward=spatial
    attention.MemEffAttention.forward=spatial
    temporal.XFORMERS_AVAILABLE=False;motion_module.XFORMERS_AVAILABLE=False
    temporal.CrossAttention._attention=temporal_attention
    model=VideoDepthAnything(encoder='vitl',features=256,out_channels=[256,512,1024,1024])
    model.load_state_dict(torch.load(ex.local(checkpoint,exists=True),map_location='cpu',weights_only=True),strict=True)
    return model.eval()


def main(path):
    seq_load.install('depth')   # gcsfuse mmap 로딩 병목 회피: 체크포인트 torch.load를 순차-read로
    cfg=ex.read_json(ex.local(path,exists=True));work=ex.local(cfg['work'],exists=True)
    torch.set_num_threads(8)
    if not torch.cuda.is_available():raise RuntimeError('Video Depth Anything Large 실행에 CUDA GPU가 필요합니다.')
    model=load_model(cfg['checkpoint']).to('cuda')
    cap=cv2.VideoCapture(str(ex.local(cfg['source'],exists=True)));frames=[]
    try:
        while True:
            ok,frame=cap.read()
            if not ok:break
            frames.append(cv2.cvtColor(frame,cv2.COLOR_BGR2RGB))
    finally:cap.release()
    if len(frames)!=cfg['frames']:raise ValueError('깊이 입력 프레임 수 불일치')
    with torch.inference_mode():
        depth,fps=model.infer_video_depth(np.asarray(frames),cfg['fps'],input_size=518,device='cuda',fp32=False)
    if not np.isfinite(depth).all():raise ValueError('깊이 출력에 NaN/Inf가 있습니다.')
    np.save(work/'inverse_depth.npy',depth.astype(np.float32))
    ex.write_json(work/'result.json',dict(frames=len(depth),fps=fps,shape=list(depth.shape),
        convention='relative inverse depth: larger is nearer',model='Video Depth Anything Large',precision='FP16'))


if __name__=='__main__':main(sys.argv[1])
