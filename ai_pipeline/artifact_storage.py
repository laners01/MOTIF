"""S3 transport for immutable precomputed background artifacts."""
import json
from pathlib import Path
from .s3_storage import parse_s3_uri

def sibling_uri(uri: str, suffix: str) -> str:
    bucket, key = parse_s3_uri(uri)
    return f"s3://{bucket}/{Path(key).with_suffix(suffix).as_posix()}"

def download_file(client, uri: str, destination: Path) -> Path:
    bucket, key = parse_s3_uri(uri)
    destination.parent.mkdir(parents=True, exist_ok=True)
    client.download_file(bucket, key, str(destination))
    return destination

def upload_file(client, source: Path, uri: str, content_type: str) -> None:
    bucket, key = parse_s3_uri(uri)
    client.upload_file(str(source), bucket, key, ExtraArgs={"ContentType": content_type})

def read_json(client, uri: str) -> dict:
    bucket, key = parse_s3_uri(uri)
    return json.loads(client.get_object(Bucket=bucket, Key=key)["Body"].read())

def write_json(client, uri: str, value: dict) -> None:
    bucket, key = parse_s3_uri(uri)
    client.put_object(Bucket=bucket, Key=key, Body=json.dumps(value, ensure_ascii=False, indent=2).encode(), ContentType="application/json")
