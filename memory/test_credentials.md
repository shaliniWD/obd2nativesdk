# Test Credentials

## WiseDrive Internal API (Dev)
- Endpoint: https://faircar.in:9768/api/obd/encrypted
- Auth: JWT Bearer Token
- Login: POST https://faircar.in:9768/api/auth/login
  - username: partner_api
  - password: (stored in environment / ask team)
- License plate: sent as URL parameter ?license_plate=XXX

## JFrog Artifactory
- User: kalyan@wisedrive.in
- Token: Set via environment variable JFROG_TOKEN or in local.properties (NOT committed to git)
- Snapshots: https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots
- Releases: https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases

## Test RSA Keys
- Private Key: /app/wisedrive-obd2-sdk-android/test_files/test_private_key.pem
- Public Key: /app/wisedrive-obd2-sdk-android/test_files/test_public_key.pem
