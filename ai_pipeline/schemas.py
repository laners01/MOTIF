"""MOTIF v0.3 compose request and worker result models."""
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator

class Anchor(BaseModel):
    model_config = ConfigDict(extra="ignore")
    x: float = Field(ge=0, le=1)
    y: float = Field(ge=0, le=1)
    r: float = Field(gt=0, le=1)

class Slot(BaseModel):
    model_config = ConfigDict(extra="ignore")
    participant_mask: int = Field(gt=0)
    anchor: Anchor | None = None

class ProcessRequest(BaseModel):
    """Request received by intake and passed unchanged in MOTIF_JOB."""
    model_config = ConfigDict(extra="ignore")
    job_uuid: str = Field(min_length=1, max_length=128)
    job_type: Literal["CHALLENGE", "MOTIF"]
    attempt: int = Field(ge=1)
    raw_video_key: str = Field(min_length=1)
    base_video_key: str = Field(min_length=1)
    output_video_key: str = Field(min_length=1)
    correction_enabled: bool
    slot: Slot | None = None  # v0.5: CHALLENGE는 단일 slot, MOTIF는 null. (구 participant_mask+slots[] 대체)
    base_duration_ms: int = Field(gt=0)

    @model_validator(mode="after")
    def validate_contract(self):
        if self.job_type == "CHALLENGE" and self.slot is None:
            raise ValueError("CHALLENGE 요청에는 slot이 필요합니다.")
        for key in (self.raw_video_key, self.base_video_key, self.output_video_key):
            if key.startswith("/") or ".." in key.split("/"):
                raise ValueError("S3 key는 상대 key여야 합니다.")
        if not self.base_video_key.lower().endswith(".mp4"):
            raise ValueError("base_video_key는 MP4여야 합니다.")
        if not self.output_video_key.lower().endswith(".mp4"):
            raise ValueError("output_video_key는 MP4여야 합니다.")
        return self

class ProcessResponse(BaseModel):
    result_video_key: str
    processing_ms: int
    attempt: int
    worker_id: str
