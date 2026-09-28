"""Restore this package's XZ-compressed NPY files, checking their original SHA-256."""
from pathlib import Path
import hashlib
import json
import lzma
import os


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def restore(package=None):
    package = Path(package or Path(__file__).resolve().parent).resolve()

    def local(name):
        path = (package / name).resolve()
        if not path.is_relative_to(package):
            raise ValueError('Package path escapes its directory: ' + name)
        return path

    storage_file = package / 'storage.json'
    if not storage_file.exists():
        print('Arrays already restored:', package.name, flush=True)
        return package
    storage = json.loads(storage_file.read_text())
    manifest_file = package / 'manifest.json'
    manifest = json.loads(manifest_file.read_text())
    for name, row in storage['arrays'].items():
        target = local(name)
        if target.exists():
            if digest(target) != row['original']['sha256']:
                raise ValueError('Existing array differs from the original: ' + name)
        else:
            compressed = local(row['compressed'])
            if digest(compressed) != row['compressed_sha256']:
                raise ValueError('Compressed file checksum mismatch: ' + name)
            temporary = target.with_suffix(target.suffix + '.restoring')
            checksum = hashlib.sha256()
            with lzma.open(compressed, 'rb') as source, temporary.open('wb') as output:
                while block := source.read(4 * 1024 * 1024):
                    output.write(block)
                    checksum.update(block)
            if checksum.hexdigest() != row['original']['sha256']:
                temporary.unlink()
                raise ValueError('Restored array checksum mismatch: ' + name)
            os.utime(temporary, ns=(row['original']['stat'][1], row['original']['stat'][1]))
            temporary.replace(target)
        stat = target.stat()
        manifest['files'][name] = dict(stat=[stat.st_size, stat.st_mtime_ns], sha256=row['original']['sha256'])
        manifest['files'].pop(row['compressed'], None)
        print('Restored:', name, flush=True)
    manifest['status'] = storage['restored_status']
    manifest['export']['array_storage'] = 'NPY, restored byte-for-byte'
    manifest['files'].pop('storage.json', None)
    temporary = manifest_file.with_suffix('.restoring')
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    temporary.replace(manifest_file)
    # Release the compressed copy only after all original files are verified.
    for row in storage['arrays'].values():
        local(row['compressed']).unlink(missing_ok=True)
    storage_file.unlink()
    print('Ready for editing:', package, flush=True)
    return package


if __name__ == '__main__':
    restore()
