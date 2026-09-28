"""S3 영상 경로 검증과 전송 기능."""

import os
import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlparse

# CHALLENGE base 키: private/edited/{challenge_seq}/{participant_seq}/{파일}
# 사전계산 데이터는 같은 seq 쌍으로 precomputed/{challenge_seq}/{participant_seq}/ 아래에 둔다.
_EDITED_SEQ = re.compile(r"(?:^|/)edited/(\d+)/(\d+)/[^/]+$")


def parse_s3_uri(uri: str) -> tuple[str, str]:
    """S3 URI를 버킷과 객체 키로 분리한다."""
    parsed = urlparse(uri)
    key = parsed.path.lstrip("/")
    if parsed.scheme != "s3" or not parsed.netloc or not key or parsed.query or parsed.fragment:
        raise ValueError(f"올바르지 않은 S3 URI입니다: {uri}")
    return parsed.netloc, key


def video_suffix(key: str) -> str:
    """지원하는 입력 영상 확장자를 반환한다."""
    suffix = Path(key).suffix.lower()
    if suffix not in {".mp4", ".mov", ".mkv", ".avi", ".webm"}:
        raise ValueError(f"지원하지 않는 영상 확장자입니다: {suffix or '(없음)'}")
    return suffix


def download_pair(client, foreground: tuple[str, str, Path], background: tuple[str, str, Path]) -> None:
    """서로 독립적인 두 입력 영상을 동시에 내려받는다."""
    with ThreadPoolExecutor(max_workers=2, thread_name_prefix="s3-download") as pool:
        futures = [
            pool.submit(client.download_file, bucket, key, str(destination))
            for bucket, key, destination in (foreground, background)
        ]
        for future in futures:
            future.result()


def upload_video(client, source: Path, bucket: str, key: str) -> None:
    """완성된 MP4를 S3에 업로드한다."""
    client.upload_file(str(source), bucket, key, ExtraArgs={"ContentType": "video/mp4"})



def configured_bucket() -> str:
    bucket = os.environ.get("S3_BUCKET", "").strip()
    if not bucket:
        raise RuntimeError("필수 환경 변수가 없습니다: S3_BUCKET")
    return bucket


def _precomputed_dir(base_video_key: str) -> str:
    """CHALLENGE base 키에서 (challenge, participant) seq를 뽑아 사전계산 폴더를 만든다.

    형식이 다르면 조용히 틀린 폴더를 만들지 않고 즉시 실패한다(신뢰 경계).
    """
    match = _EDITED_SEQ.search(base_video_key)
    if not match:
        raise ValueError(
            f"base_video_key에서 challenge/participant seq를 찾지 못했습니다: {base_video_key}"
        )
    prefix = os.environ.get("PRECOMPUTED_PREFIX", "precomputed").strip("/")
    return f"{prefix}/{match.group(1)}/{match.group(2)}"


def precomputed_manifest_key(base_video_key: str) -> str:
    """base 키를 (챌린지, 슬롯) seq 경로의 사전계산 manifest로 매핑한다."""
    return f"{_precomputed_dir(base_video_key)}/ready.json"


def precomputed_artifact_key(base_video_key: str, content_sha256: str) -> str:
    return f"{_precomputed_dir(base_video_key)}/artifacts/{content_sha256}.tar.gz"
