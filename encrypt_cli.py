import argparse
import os
from cryptography.fernet import Fernet

def encrypt_string(key, plaintext):
    """Encrypts a string using Fernet symmetric encryption."""
    f = Fernet(key)
    ciphertext = f.encrypt(plaintext.encode())
    return ciphertext.decode()

def decrypt_string(key, ciphertext):
    """Decrypts a string using Fernet symmetric encryption."""
    f = Fernet(key)
    plaintext = f.decrypt(ciphertext.encode())
    return plaintext.decode()


def main():
    parser = argparse.ArgumentParser(description="Encrypt or decrypt a string.")
    parser.add_argument("action", choices=["encrypt", "decrypt"], help="Action to perform: encrypt or decrypt")
    parser.add_argument("key", help="Encryption key (must be 44 URL-safe base64 characters). If 'generate', a new key will be generated.")
    parser.add_argument("input", help="String to encrypt or decrypt")

    args = parser.parse_args()

    if args.key == "generate":
        key = Fernet.generate_key().decode()
        print(f"Generated key: {key}") #Important to save this key
        return # Exit after generating key
    else:
        key = args.key

    try:
        if args.action == "encrypt":
            result = encrypt_string(key, args.input)
            print(f"Ciphertext: {result}")
        elif args.action == "decrypt":
            result = decrypt_string(key, args.input)
            print(f"Plaintext: {result}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()