# Test Credentials

## WiseDrive Internal API (Dev)
- Endpoint: https://faircar.in:9768/api/obd/encrypted
- Auth: JWT Bearer Token
- Login: POST https://faircar.in:9768/api/auth/login
  - username: partner_api
  - password: Partner@2025!
- License plate: sent as URL parameter ?license_plate=XXX

## JFrog Artifactory
- User: kalyan@wisedrive.in
- Token: cb02a0f4-5bc9-44eb-a6f0-70c88b811b55

## Test RSA Keys
- Private Key: /app/wisedrive-obd2-sdk-android/test_files/test_private_key.pem
- Public Key: /app/wisedrive-obd2-sdk-android/test_files/test_public_key.pem

## Legacy Credentials (old endpoint - deprecated)
- Old Endpoint: http://faircar.in:82/apiv2/webhook/obdreport/wisedrive/encrypted
- Old Auth: Basic prasad:prasad@123
