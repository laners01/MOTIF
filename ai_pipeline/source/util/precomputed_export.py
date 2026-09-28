"""CPU-only portable exports for run_pre; registered inference assets stay unchanged."""
from concurrent.futures import ThreadPoolExecutor, as_completed
import copy
import hashlib
import json
import lzma
from pathlib import Path
import shutil
import subprocess
import time
import zipfile

import cv2
import numpy as np
from source.util import removal_experiments as ex
from source.relighting import model_test as rel

PERSON_PATHS = ('masks', 'crop', 'removal_masks', 'driver_pose', 'pose', 'features',
                'geometry', 'reference', 'parts', 'background_frames', 'condition_frames', 'grain', 'latents')
TOP_PATHS = ('music', 'preview', 'camera', 'depth', 'text')
RESTORE = Path(__file__).with_name('restore_precomputed_arrays.py')


def package_name(source):
    return 'precomputed_' + Path(source).stem.removeprefix('original_')


def default_destination(source):
    source = ex.local(source, exists=True)
    parent = source.parent if source.parent.name.endswith('데이터') else ex.local('output/background_precomputed_exports')
    return parent / package_name(source)


def _receipt(path):
    stat = path.stat()
    return dict(stat=[stat.st_size, stat.st_mtime_ns], sha256=ex.digest(path))


def verify_package(directory):
    directory = ex.local(directory, exists=True)
    manifest = ex.read_json(directory/'manifest.json')
    if manifest['status'] != 'compressed': raise ValueError('무손실 압축 완료 자료가 아닙니다.')
    # 노트북 export 시 딸려오는 .ipynb_checkpoints/ 잔재는 실데이터가 아니므로 무결성 대조에서 제외.
    actual = {str(p.relative_to(directory)) for p in directory.rglob('*')
              if p.is_file() and '.ipynb_checkpoints' not in p.parts} - {'manifest.json', 'README.md'}
    if actual != set(manifest['files']): raise ValueError('배포본 파일 목록 불일치')
    for name, expected in manifest['files'].items():
        path = ex.local(directory/name, exists=True)
        if not path.is_relative_to(directory) or _receipt(path) != expected:
            raise ValueError('배포본 파일 검증 실패: '+name)
    return manifest


def _portable(value, asset):
    if isinstance(value, dict): return {key:_portable(item,asset) for key,item in value.items()}
    if isinstance(value, list): return [_portable(item,asset) for item in value]
    if isinstance(value, str):
        if value == asset['source']: return 'source.mp4'
        if value == asset['directory']: return '.'
        prefix = asset['directory']+'/'
        if value.startswith(prefix): return value[len(prefix):]
    return value


def _run_ffmpeg(command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or '').strip()[-1000:]
        raise RuntimeError(f'ffmpeg 실패(exit {result.returncode}): {detail}')
    return result


def _playback(source, audio, target):
    import imageio_ffmpeg
    expected,_ = rel.video_info(source)
    duration = expected['frames']/expected['fps']
    command = [imageio_ffmpeg.get_ffmpeg_exe(), '-nostdin', '-hide_banner', '-loglevel', 'error', '-n',
        '-threads', '2', '-i', str(source), '-i', str(audio), '-map', '0:v:0', '-map', '1:a:0?',
        '-vf', 'pad=ceil(iw/2)*2:ceil(ih/2)*2', '-filter_threads', '1', '-fps_mode', 'passthrough',
        '-c:v', 'libx264', '-preset', 'fast', '-crf', '18', '-threads', '4', '-profile:v', 'main',
        '-pix_fmt', 'yuv420p', '-tag:v', 'avc1', '-af', f'apad,atrim=duration={duration:.9f}',
        '-c:a', 'aac', '-b:a', '192k', '-ac', '2', '-movflags', '+faststart', str(target)]
    _run_ffmpeg(command)
    actual,_ = rel.video_info(target)
    if (actual['frames'] != expected['frames'] or abs(actual['fps']-expected['fps']) > 1e-4
            or actual['size'] != [v+v%2 for v in expected['size']]):
        raise ValueError('재생 영상 프레임/FPS/해상도 불일치')
    if ex.has_audio(audio) != ex.has_audio(target): raise ValueError('배경 오디오 누락')
    _run_ffmpeg([imageio_ffmpeg.get_ffmpeg_exe(), '-nostdin', '-v', 'error', '-xerror',
        '-threads', '2', '-i', str(target), '-map', '0:v:0', '-map', '0:a:0?', '-f', 'null', '-'])


def _copy_data(source, target, expected):
    if ex.digest(source) != expected['sha256']: raise ValueError('원본 준비 자료가 변경됐습니다: '+str(source))
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.suffix == '.png':
        original = cv2.imread(str(source), cv2.IMREAD_UNCHANGED)
        if original is None: raise ValueError('PNG 읽기 실패: '+str(source))
        ok,encoded = cv2.imencode('.png',original,[cv2.IMWRITE_PNG_COMPRESSION,6])
        restored = cv2.imdecode(encoded,cv2.IMREAD_UNCHANGED) if ok else None
        if restored is None or restored.dtype != original.dtype or not np.array_equal(restored,original):
            raise ValueError('PNG 픽셀/알파 무손실 검사 실패: '+str(source))
        if encoded.nbytes < source.stat().st_size: target.write_bytes(encoded.tobytes())
        else: shutil.copy2(source,target)
    elif source.suffix == '.npy':
        array = np.load(source,mmap_mode='r',allow_pickle=False)
        filters = [dict(id=lzma.FILTER_DELTA,dist=array.dtype.itemsize),dict(id=lzma.FILTER_LZMA2,preset=1)]
        del array
        target = target.with_suffix('.npy.xz')
        xz = shutil.which('xz') if source.stat().st_size >= 64*1024*1024 else None
        if xz:
            with source.open('rb') as stream,target.open('wb') as output:
                subprocess.run([xz,'-T4','-c','--block-size=32MiB',
                    '--delta=dist='+str(filters[0]['dist']),'--lzma2=preset=1'],
                    stdin=stream,stdout=output,stderr=subprocess.PIPE,check=True)
        else:
            with source.open('rb') as stream,lzma.open(target,'wb',filters=filters) as output:
                shutil.copyfileobj(stream,output,4*1024*1024)
        checksum = hashlib.sha256()
        with lzma.open(target,'rb') as stream:
            while block := stream.read(4*1024*1024): checksum.update(block)
        if checksum.hexdigest() != expected['sha256']: raise ValueError('NPY 무손실 복원 검사 실패: '+str(source))
    else:
        shutil.copy2(source,target)
        if ex.digest(target) != expected['sha256']: raise ValueError('복사 파일 SHA-256 불일치')
    return target


def export_package(asset, destination=None):
    """Export only runtime inputs, with pixel-exact PNG and byte-exact NPY/XZ."""
    if asset['status'] != 'complete': raise ValueError('완료된 사전 계산 자료가 필요합니다.')
    source = ex.local(asset['source'],exists=True)
    destination = ex.local(destination or default_destination(source))
    signature = hashlib.sha256(json.dumps([ex.digest(asset['manifest']),ex.digest(__file__),ex.digest(RESTORE)]).encode()).hexdigest()
    if destination.exists():
        saved = ex.read_json(destination/'manifest.json')
        if saved.get('export',{}).get('signature') != signature:
            raise FileExistsError('다른 입력/버전의 내보내기 폴더가 있습니다: '+str(destination))
        verify_package(destination)
        print('압축 자료 재사용:',destination,flush=True)
        return dict(directory=str(destination.relative_to(ex.ROOT)),manifest=str((destination/'manifest.json').relative_to(ex.ROOT)),
                    status='compressed',reused=True,bytes=sum(p.stat().st_size for p in destination.rglob('*') if p.is_file()))
    started = time.perf_counter()
    work = ex.unique_dir('work/precomputed_export','package');staging=work/'data';staging.mkdir()
    base = ex.local(asset['directory'],exists=True)
    selected = set()
    for name in [asset['record']['frame_dir'],*[asset.get(k) for k in TOP_PATHS],
                 *[person[k] for person in asset['people'].values() for k in PERSON_PATHS]]:
        if name is None: continue
        path = ex.local(name,exists=True)
        if not path.is_relative_to(base): raise ValueError('사전 계산 폴더 밖의 파일: '+str(path))
        selected.update(ex.sequence(path,asset['record']['frames']) if path.is_dir() else [path])
    storage = dict(format='lossless-npy-xz-v1',restored_status='complete',arrays={})
    print('무손실 내보내기:',destination,'·',len(selected),'개 파일',flush=True)
    with ThreadPoolExecutor(max_workers=12) as images,ThreadPoolExecutor(max_workers=3) as arrays:
        pending = {}
        for path in sorted(selected):
            name = str(path.relative_to(base));expected=asset['files'][str(path.relative_to(ex.ROOT))]
            pool = arrays if path.suffix == '.npy' else images
            pending[pool.submit(_copy_data,path,staging/name,expected)] = (name,expected)
        for i,future in enumerate(as_completed(pending),1):
            name,expected=pending[future];target=future.result()
            if name.endswith('.npy'):
                storage['arrays'][name]=dict(original=expected,compressed=str(target.relative_to(staging)),compressed_sha256=ex.digest(target))
            if i%1000==0: print('무손실 검증:',i,'/',len(pending),flush=True)
    if ex.digest(source) != asset['source_sha256']: raise ValueError('입력 원본이 변경됐습니다.')
    shutil.copy2(source,staging/'source.mp4')
    _playback(ex.local(asset['record']['source']),source,staging/'original.mp4')
    manifest = _portable({key:copy.deepcopy(value) for key,value in asset.items() if key not in ('files','times','people')},asset)
    manifest['people'] = {key:_portable({k:v for k,v in person.items() if k in (*PERSON_PATHS,'id','directory','bbox','rect')},asset)
                          for key,person in asset['people'].items()}
    manifest.update(status='compressed',directory='.',manifest='manifest.json')
    manifest['record'].pop('work',None)
    manifest['record']['source_sha256']=ex.digest(staging/'original.mp4')
    manifest['export']=dict(format='portable-precomputed-v1',path_base='manifest_directory',signature=signature,
        analysis_master_sha256=asset['record']['source_sha256'],
        playback='H.264 Main / 8-bit yuv420p / AAC-LC / faststart',
        array_storage='XZ; run restore_arrays.py before editing',png_storage='pixel-exact PNG',
        note='PNG pixels/alpha and restored NPY bytes match the analysis inputs. Playback MP4 is re-encoded.')
    ex.write_json(staging/'storage.json',storage)
    shutil.copyfile(RESTORE,staging/'restore_arrays.py')
    manifest['files']={str(p.relative_to(staging)):_receipt(p) for p in sorted(staging.rglob('*')) if p.is_file()}
    ex.write_json(staging/'manifest.json',manifest)
    (staging/'README.md').write_text(f'''# {destination.name}

유저 영상 편집용 사전 계산 자료입니다. 모든 검출 인물의 제거 버전을 포함합니다.

- `source.mp4`: 입력 원본을 그대로 복사한 파일입니다.
- `original.mp4`: 재생 호환용 H.264/AAC 기준 영상입니다. 소리를 가져올 때 사용합니다.
- `people.jpg`: 제거할 인물의 ID 확인용 이미지입니다(검출 미리보기가 있는 경우).
- `frames/`, `people/ID/background_frames/`: 원본 프레임과 해당 인물을 제거한 인페인팅 배경입니다.
- `masks`, `removal_masks`, `crop`, 관절·기하·접지·깊이·카메라·음악·조명 자료를 유지했습니다.
- 중복 추적 미리보기·실행 기록·재생용 제거 영상은 포함하지 않았습니다.

PNG는 바로 읽을 수 있으며 모든 픽셀과 투명도가 원본과 같습니다. PNG 파일 압축 바이트는 달라집니다.
배열은 `.npy.xz`로 보관합니다. 편집 전에 `python restore_arrays.py`를 실행하면 원본 SHA-256까지 같은 `.npy`로 복원됩니다.
복원 도구는 Python 3.11 이상 기본 기능만 사용합니다. 복원 후 압축 배열을 삭제하고 매니페스트를 준비 완료 상태로 갱신합니다.
영상 재생에는 배열 복원이 필요 없습니다. 모델·실행 환경은 별도입니다.
경로는 이 폴더 기준입니다. 다운로드 자료를 서비스에 등록할 때 이 폴더 기준으로 경로와 파일 목록을 읽어야 합니다.
''')
    verify_package(staging)
    destination.parent.mkdir(parents=True,exist_ok=True)
    staging.rename(destination)
    result=dict(directory=str(destination.relative_to(ex.ROOT)),manifest=str((destination/'manifest.json').relative_to(ex.ROOT)),
        status='compressed',seconds=time.perf_counter()-started,files=len(manifest['files']),
        people=len(manifest['people']),bytes=sum(p.stat().st_size for p in destination.rglob('*') if p.is_file()),
        png_verified=sum(p.suffix=='.png' for p in selected),arrays_verified=len(storage['arrays']))
    ex.write_json(work/'verification.json',result)
    print('압축 자료 완료:',destination,flush=True)
    return result


def create_download(group):
    """Build and CRC-check a ZIP64 download of a complete dataset folder."""
    group=ex.local(group,exists=True)
    if not group.is_dir() or not group.is_relative_to(ex.local('output')): raise ValueError('output/ 안의 데이터 폴더가 필요합니다.')
    packages=sorted(group.glob('precomputed_*'))
    if not packages: raise ValueError('완료된 precomputed 자료가 없습니다.')
    for package in packages: verify_package(package)
    (group/'README.md').write_text('# '+group.name+'\n\n'+
        '\n'.join('- `'+p.name+'`' for p in packages)+
        '\n\n각 폴더에 인물별 제거 배경과 후속 편집용 분석 자료를 저장했습니다. PNG는 무손실, 배열은 XZ로 압축했습니다.\n'+
        'ZIP을 푼 뒤 편집에 사용할 폴더의 `restore_arrays.py`를 Python 3.11 이상으로 실행하세요. 모델·실행 환경은 별도입니다.\n')
    paths=sorted(p for p in group.rglob('*') if p.is_file())
    if any(p.is_symlink() or not p.resolve().is_relative_to(group) for p in paths): raise ValueError('외부 링크가 있는 데이터 폴더')
    work=ex.unique_dir('work/precomputed_export','download');temporary=work/'download.zip'
    expected={str(p.relative_to(group.parent)):p.stat().st_size for p in paths}
    print('다운로드 ZIP 생성:',group.name,flush=True)
    with zipfile.ZipFile(temporary,'x',allowZip64=True) as output:
        for path in paths:
            method=zipfile.ZIP_DEFLATED if path.suffix in ('.json','.md','.py') else zipfile.ZIP_STORED
            output.write(path,str(path.relative_to(group.parent)),compress_type=method,compresslevel=6)
    with zipfile.ZipFile(temporary) as check:
        if check.testzip() is not None or {p.filename:p.file_size for p in check.infolist()} != expected:
            raise ValueError('다운로드 ZIP 검증 실패')
    checksum=ex.digest(temporary);archive=group.with_suffix('.zip');temporary.replace(archive)
    archive.with_suffix('.zip.sha256').write_text(f'{checksum}  {archive.name}\n')
    result=dict(path=str(archive.relative_to(ex.ROOT)),bytes=archive.stat().st_size,sha256=checksum,
                files=len(paths),packages=len(packages),crc='passed')
    ex.write_json(work/'verification.json',result)
    print('다운로드 완료:',archive,flush=True)
    return result
