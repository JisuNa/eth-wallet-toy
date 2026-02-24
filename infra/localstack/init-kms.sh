#!/bin/bash
set -euo pipefail

KEY_ID=$(awslocal kms create-key --query 'KeyMetadata.KeyId' --output text)
awslocal kms create-alias --alias-name alias/wallet-data-key --target-key-id "$KEY_ID"
