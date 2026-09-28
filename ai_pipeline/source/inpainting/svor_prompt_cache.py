"""SVOR 프롬프트/네거티브가 고정이므로 T5 출력 임베딩을 S3에 캐시한다.

캐시가 있으면 text encoder(.pth 10.58GB, gcsfuse 순차라도 ~3.6분) 로딩을 통째로 건너뛴다.
임베딩은 프롬프트당 ~4MB로 작다. 모든 함수는 실패해도 예외를 삼키고 None/no-op → 정상 경로로 폴백.
"""
import hashlib
import io
import os

import torch


def _key(prompt, negative_prompt, dtype, model_name, max_seq=512):
    parts = [prompt or "", negative_prompt or "", str(dtype),
             os.path.basename(str(model_name).rstrip("/")), str(max_seq),
             os.environ.get("MODEL_BUNDLE_VERSION", "models-v1")]
    digest = hashlib.sha256("\x00".join(parts).encode("utf-8")).hexdigest()
    prefix = os.environ.get("PRECOMPUTED_PREFIX", "precomputed").strip("/")
    return f"{prefix}/svor_prompt_cache/{digest}.pt"


def _serialize(prompt_embeds, negative_prompt_embeds):
    buf = io.BytesIO()
    torch.save({"prompt_embeds": [t.cpu() for t in prompt_embeds],
                "negative_prompt_embeds": None if negative_prompt_embeds is None
                else [t.cpu() for t in negative_prompt_embeds]}, buf)
    return buf.getvalue()


def _deserialize(data):
    obj = torch.load(io.BytesIO(data), map_location="cpu")
    return obj["prompt_embeds"], obj["negative_prompt_embeds"]


def _client_bucket():
    bucket = os.environ.get("S3_BUCKET", "").strip()
    if not bucket:
        return None, None
    import boto3
    return boto3.client("s3", region_name=os.environ.get("AWS_REGION") or None), bucket


def load(prompt, negative_prompt, dtype, model_name):
    """캐시된 (prompt_embeds, negative_prompt_embeds) 반환. 없거나 실패하면 None."""
    try:
        client, bucket = _client_bucket()
        if client is None:
            return None
        obj = client.get_object(Bucket=bucket, Key=_key(prompt, negative_prompt, dtype, model_name))
        return _deserialize(obj["Body"].read())
    except Exception:
        return None


def save(prompt, negative_prompt, dtype, model_name, prompt_embeds, negative_prompt_embeds):
    """임베딩을 S3에 저장. 실패는 조용히 무시(no-op)."""
    try:
        client, bucket = _client_bucket()
        if client is None:
            return False
        client.put_object(Bucket=bucket, Key=_key(prompt, negative_prompt, dtype, model_name),
                          Body=_serialize(prompt_embeds, negative_prompt_embeds),
                          ContentType="application/octet-stream")
        return True
    except Exception:
        return False


def _selfcheck():
    a = [torch.randn(3, 4), torch.randn(2, 4)]
    b = [torch.randn(3, 4)]
    pe, npe = _deserialize(_serialize(a, b))
    assert torch.allclose(pe[0], a[0]) and torch.allclose(pe[1], a[1]) and torch.allclose(npe[0], b[0])
    pe2, npe2 = _deserialize(_serialize(a, None))
    assert npe2 is None and torch.allclose(pe2[0], a[0])
    k1 = _key("p", "n", torch.bfloat16, "/models/svor/")
    assert k1 == _key("p", "n", torch.bfloat16, "/models/svor") and k1.endswith(".pt")
    assert k1 != _key("p2", "n", torch.bfloat16, "/models/svor")
    assert k1 != _key("p", "n", torch.float16, "/models/svor")
    print("svor_prompt_cache selfcheck OK")


if __name__ == "__main__":
    _selfcheck()
