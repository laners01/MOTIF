"""Precompute both existing DWPose input conventions, once per background person."""
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT),str(ROOT/'vendor/MimicMotion')]
import cv2
import numpy as np
from source.util import removal_experiments as ex
from source.enhancement.correction_worker import extract
from source.enhancement.placement_worker import detect


def main(path):
    from mimicmotion.dwpose.wholebody import Wholebody
    cfg=ex.read_json(path)
    model=Wholebody(str(ex.local('vendor/MimicMotion/models/DWPose/yolox_l.onnx')),
                    str(ex.local('vendor/MimicMotion/models/DWPose/dw-ll_ucoco_384.onnx')),'cpu')
    frames=ex.sequence(cfg['frames'])
    for person in cfg['people']:
        directory=ex.local(person['directory'])
        np.save(directory/'driver_pose.npy',extract(person['crop'],model))
        poses=[]
        for i,(image,mask) in enumerate(zip(frames,ex.sequence(person['masks'],len(frames)))):
            poses.append(detect(cv2.imread(str(image)),cv2.imread(str(mask),0),model))
            if i%30==0:print('원본 위치 관절:',person['id'],i+1,'/',len(frames),flush=True)
        np.save(directory/'pose.npy',np.asarray(poses))


if __name__=='__main__':main(sys.argv[-1])
