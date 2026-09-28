"""Portable, immutable packaging for background precomputation."""

import hashlib
import json
import os
import tarfile
from pathlib import Path, PurePosixPath


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _relative(root: Path, path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"artifact 경로가 프로젝트를 벗어납니다: {resolved}")
    return resolved.relative_to(root.resolve())


def create_archive(root: Path, archive: Path, paths: list[Path]) -> Path:
    """Archive only regular files/directories beneath root; preserve mtimes."""
    archive.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "w:gz", compresslevel=1) as bundle:
        for path in paths:
            relative = _relative(root, path)
            bundle.add(path, arcname=relative.as_posix(), recursive=True)
    return archive


def _safe_member(root: Path, member: tarfile.TarInfo) -> None:
    name = PurePosixPath(member.name)
    if name.is_absolute() or ".." in name.parts or member.issym() or member.islnk():
        raise ValueError(f"안전하지 않은 artifact 항목: {member.name}")
    destination = (root / Path(*name.parts)).resolve()
    if not destination.is_relative_to(root.resolve()):
        raise ValueError(f"artifact 경로 탈출: {member.name}")


def extract_archive(root: Path, archive: Path) -> None:
    with tarfile.open(archive, "r:gz") as bundle:
        members = bundle.getmembers()
        for member in members:
            _safe_member(root, member)
        bundle.extractall(root, members=members, filter="data")


def restore_receipts(root: Path, manifest: dict) -> None:
    """Restore exact mtimes expected by prepost.prepared after archive transfer."""
    for relative, receipt in manifest["files"].items():
        path = (root / relative).resolve()
        if not path.is_relative_to(root.resolve()) or not path.is_file():
            raise FileNotFoundError(relative)
        size, mtime_ns = receipt["stat"]
        if path.stat().st_size != size:
            raise ValueError(f"artifact 파일 크기 불일치: {relative}")
        os.utime(path, ns=(mtime_ns, mtime_ns))


def import_portable(root: Path, package_rel: str, *, source_sha256: str,
                    implementation: str, library: str) -> tuple[str, str]:
    """Compose 측 portable package importer: 검증 → XZ 복원 → 경로 rebase → pointer 등록.

    반환: (source_rel, manifest_rel) — 프로젝트 루트 상대 경로. prepost.prepared()가 이 pointer를 읽는다.
    """
    from source.util import precomputed_export as pex
    from source.util import restore_precomputed_arrays as restore
    root = root.resolve()
    package = (root / Path(package_rel)).resolve()
    if not package.is_relative_to(root) or not package.is_dir():
        raise ValueError(f"portable package 경로가 올바르지 않습니다: {package_rel}")
    manifest_path = package / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    # tar 전송으로 흐트러진 mtime을 매니페스트 receipt로 되돌려 verify_package의 stat 검사를 통과시킨다.
    # 내용 무결성은 verify_package의 SHA-256이 별도로 보증한다.
    for name, receipt in manifest.get("files", {}).items():
        member = package / name
        if member.is_file():
            os.utime(member, ns=(receipt["stat"][1], receipt["stat"][1]))
    pex.verify_package(package)          # 압축 패키지 무결성(SHA-256 + stat)
    restore.restore(package)             # .npy.xz → .npy (원본 SHA-256 확인), status → complete
    manifest = json.loads(manifest_path.read_text())
    if manifest.get("status") != "complete":
        raise ValueError("portable 복원 후 상태가 complete가 아닙니다.")
    if manifest.get("source_sha256") != source_sha256:
        raise ValueError("portable 배경 source_sha256 불일치")
    if manifest.get("implementation") != implementation:
        raise ValueError("portable 배경 implementation 불일치")

    def rebase(name: str) -> str:
        member = PurePosixPath(name)
        if member.is_absolute() or ".." in member.parts:
            raise ValueError(f"portable 경로 탈출: {name}")
        target = (package / Path(*member.parts)).resolve()
        if not target.is_relative_to(package):
            raise ValueError(f"portable 경로 탈출: {name}")
        return target.relative_to(root).as_posix()

    manifest["directory"] = package.relative_to(root).as_posix()
    manifest["manifest"] = rebase(manifest["manifest"])
    manifest["source"] = rebase(manifest["source"])
    for key in ("music", "preview", "camera", "depth", "text"):
        if manifest.get(key):
            manifest[key] = rebase(manifest[key])
    record = manifest["record"]
    for key in ("source", "frame_dir"):
        record[key] = rebase(record[key])
    for person in manifest["people"].values():
        for key in (*pex.PERSON_PATHS, "directory"):
            if person.get(key):
                person[key] = rebase(person[key])
    manifest["files"] = {rebase(name): receipt for name, receipt in manifest["files"].items()}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    pointer = root / library / source_sha256 / "current.json"
    pointer.parent.mkdir(parents=True, exist_ok=True)
    pointer.write_text(json.dumps(dict(manifest=manifest["manifest"], sha256=sha256(manifest_path)),
                                  ensure_ascii=False, indent=2) + "\n")
    return manifest["source"], manifest["manifest"]

