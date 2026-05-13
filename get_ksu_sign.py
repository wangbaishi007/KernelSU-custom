#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import struct
import hashlib

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a


def u32(data, off):
    return struct.unpack_from("<I", data, off)[0]


def u64(data, off):
    return struct.unpack_from("<Q", data, off)[0]


def read_len_prefixed(data, off):
    size = u32(data, off)
    start = off + 4
    end = start + size
    if end > len(data):
        raise ValueError("length-prefixed field out of range")
    return data[start:end], end


def find_eocd(data):
    sig = b"PK\x05\x06"
    max_comment = 65535
    start = max(0, len(data) - 22 - max_comment)
    off = data.rfind(sig, start)
    if off < 0:
        raise ValueError("EOCD not found")
    return off


def get_central_dir_offset(data):
    eocd = find_eocd(data)
    return u32(data, eocd + 16)


def get_apk_signing_block(data):
    cd_offset = get_central_dir_offset(data)

    if cd_offset < 24:
        raise ValueError("central dir offset too small")

    magic = data[cd_offset - 16:cd_offset]
    if magic != APK_SIG_BLOCK_MAGIC:
        raise ValueError("APK Signing Block magic not found; APK may not have v2 signature")

    size2 = u64(data, cd_offset - 24)
    block_start = cd_offset - size2 - 8
    if block_start < 0:
        raise ValueError("invalid APK Signing Block size")

    size1 = u64(data, block_start)
    if size1 != size2:
        raise ValueError("APK Signing Block size mismatch")

    return data[block_start:cd_offset]


def find_v2_block(signing_block):
    off = 8
    end = len(signing_block) - 24

    while off < end:
        pair_size = u64(signing_block, off)
        off += 8

        pair_end = off + pair_size
        if pair_end > end:
            raise ValueError("APK Signing Block pair out of range")

        pair_id = u32(signing_block, off)
        value = signing_block[off + 4:pair_end]

        if pair_id == APK_SIGNATURE_SCHEME_V2_BLOCK_ID:
            return value

        off = pair_end

    raise ValueError("APK Signature Scheme v2 block not found")


def extract_first_cert_from_v2(v2_block):
    # v2_block:
    # length-prefixed sequence of signers
    signers, _ = read_len_prefixed(v2_block, 0)

    # first signer
    signer, _ = read_len_prefixed(signers, 0)

    # signer:
    # signedData length-prefixed
    # signatures length-prefixed
    # publicKey length-prefixed
    signed_data, _ = read_len_prefixed(signer, 0)

    # signedData:
    # digests length-prefixed
    # certificates length-prefixed
    # additional attributes length-prefixed
    _, off = read_len_prefixed(signed_data, 0)
    certs, _ = read_len_prefixed(signed_data, off)

    # certificates:
    # sequence of length-prefixed X.509 Certificate DER
    cert, _ = read_len_prefixed(certs, 0)
    return cert


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 get_ksu_sign.py <manager.apk>", file=sys.stderr)
        sys.exit(1)

    apk_path = sys.argv[1]

    with open(apk_path, "rb") as f:
        data = f.read()

    signing_block = get_apk_signing_block(data)
    v2_block = find_v2_block(signing_block)
    cert = extract_first_cert_from_v2(v2_block)

    size = len(cert)
    digest = hashlib.sha256(cert).hexdigest()

    print("KSU_EXPECTED_SIZE2=0x%x" % size)
    print("KSU_EXPECTED_HASH2=%s" % digest)


if __name__ == "__main__":
    main()
