"""
加密/解密服务
API Secret 使用 AES (ECB 模式) + Base64 加密存储
"""
import base64
import hashlib
from Crypto.Cipher import AES
from django.conf import settings


def _get_aes_key() -> bytes:
    """根据 settings.ENCRYPTION_KEY 派生 32字节 AES 密钥"""
    key = settings.ENCRYPTION_KEY.encode('utf-8')
    return hashlib.sha256(key).digest()[:32]


def encrypt_secret(plain_text: str) -> str:
    """
    加密 API Secret
    使用 AES-256-ECB + PKCS7 padding + Base64
    """
    if not plain_text:
        return ''

    key = _get_aes_key()
    cipher = AES.new(key, AES.MODE_ECB)

    # PKCS7 padding
    pad_len = 16 - (len(plain_text) % 16)
    padded = plain_text + (chr(pad_len) * pad_len)

    encrypted = cipher.encrypt(padded.encode('utf-8'))
    return base64.b64encode(encrypted).decode('utf-8')


def decrypt_secret(cipher_text: str) -> str:
    """
    解密 API Secret
    """
    if not cipher_text:
        return ''

    try:
        key = _get_aes_key()
        cipher = AES.new(key, AES.MODE_ECB)

        encrypted = base64.b64decode(cipher_text.encode('utf-8'))
        padded = cipher.decrypt(encrypted).decode('utf-8')

        # Remove PKCS7 padding
        pad_len = ord(padded[-1])
        return padded[:-pad_len]
    except Exception as e:
        # 兼容旧版本 Base64 (无 padding 模式)
        return cipher_text
