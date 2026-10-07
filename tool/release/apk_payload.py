"""Digest executable APK contents across permitted production/test signers.

APK v2/v3 signing blocks lie outside ZIP entries. Only top-level JAR signing
metadata is excluded; manifests, dex, resources, assets, libraries and service
metadata remain part of the digest. This does not replace signer verification.
"""
import hashlib
import re
import zipfile


def payload_digest(apk):
    digest = hashlib.sha256()
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate APK entries refused')
        for name in sorted(names):
            if name.endswith('/'):
                continue
            if name == 'META-INF/MANIFEST.MF' or re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', name):
                continue
            digest.update(name.encode('utf-8') + b'\0')
            with archive.open(name) as stream:
                entry = hashlib.sha256()
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    entry.update(chunk)
                digest.update(entry.digest())
    return digest.hexdigest()
