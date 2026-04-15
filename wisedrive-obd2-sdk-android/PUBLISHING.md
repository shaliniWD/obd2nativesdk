# WiseDrive OBD2 SDK - Publishing Guide

## Quick Start (After JFrog Setup)

### 1. Configure Credentials

Copy the template and add your credentials:

```bash
cp local.properties.template local.properties
```

Edit `local.properties`:
```properties
jfrog.user=your-email@wisedrive.in
jfrog.token=YOUR_TOKEN_FROM_JFROG
```

### 2. Update Repository URL

Edit `sdk/build.gradle.kts` and replace:
```kotlin
url = uri("https://YOUR_DOMAIN.jfrog.io/artifactory/wisedrive-sdk-releases")
```

With your actual domain (e.g., `wisedrive.jfrog.io`).

### 3. Publish SDK

```bash
# From project root
./gradlew :sdk:publishReleasePublicationToJFrogArtifactoryRepository
```

### 4. Verify Publication

Check JFrog UI or run:
```bash
curl -u YOUR_USER:YOUR_TOKEN \
  https://YOUR_DOMAIN.jfrog.io/artifactory/wisedrive-sdk-releases/com/wisedrive/obd2-sdk/
```

---

## Client Integration Instructions

Share this with your clients:

### Step 1: Add Repository

In `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        
        // WiseDrive Private Repository
        maven {
            url = uri("https://YOUR_DOMAIN.jfrog.io/artifactory/wisedrive-sdk-releases")
            credentials {
                username = "CLIENT_USERNAME"  // Provided by WiseDrive
                password = "CLIENT_TOKEN"     // Provided by WiseDrive
            }
        }
    }
}
```

### Step 2: Add Dependency

In `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.wisedrive:obd2-sdk:2.0.0")
}
```

### Step 3: Initialize SDK

```kotlin
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.SDKConfig

val config = SDKConfig(
    apiKey = "your-api-key",
    useMock = false
)

val sdk = WiseDriveOBD2SDK.initialize(context, config)
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 2.0.0 | 2026-04 | Military-grade encryption, dual key system |
| 1.0.0 | 2026-01 | Initial release |

---

## Troubleshooting

### "401 Unauthorized" when publishing
- Check credentials in `local.properties`
- Verify token hasn't expired
- Ensure user has write permissions

### "Could not resolve dependency" for clients
- Verify repository URL is correct
- Check client credentials
- Ensure version exists in repository

---

## Support

- Email: sdk@wisedrive.in
- Documentation: https://wisedrive.in/sdk/docs
