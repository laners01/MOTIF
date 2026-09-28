"""Background precompute request passed directly to the precompute Job."""
from pydantic import BaseModel, ConfigDict, Field

class PrecomputeRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    job_uuid: str = Field(min_length=1, max_length=128)
    attempt: int = Field(default=1, ge=1)
    base_video_key: str = Field(min_length=1)
    source_video_key: str | None = Field(default=None, min_length=1)

class PrecomputeResponse(BaseModel):
    job_uuid: str
    base_video_key: str
    manifest_key: str
    background_sha256: str
    people: list[int]
    processing_ms: int
