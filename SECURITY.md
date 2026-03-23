# Security Policy

## Reporting Security Issues

If you discover a security vulnerability, please email the maintainers directly rather than opening a public issue.

## API Key Security

### Current Implementation

This project uses the following approach to secure API keys:

1. **local.properties** - Stores Google Maps and Firebase API keys locally
   - This file is in `.gitignore` and should NEVER be committed
   - Use `local.properties.template` as a reference

2. **google-services.json** - Firebase configuration
   - This file is in `.gitignore` and should NEVER be committed
   - Use `app/google-services.json.template` as a reference

### Setup for Developers

1. Copy template files:
   ```bash
   cp local.properties.template local.properties
   cp app/google-services.json.template app/google-services.json
   ```

2. Fill in your API keys in `local.properties`

3. Download your actual `google-services.json` from Firebase Console

### Best Practices

✅ **DO:**
- Keep API keys in `local.properties` only
- Use API key restrictions in Google Cloud Console
- Restrict keys to specific Android app package names and SHA-1 fingerprints
- Regenerate keys immediately if exposed
- Use separate API keys for development and production

❌ **DON'T:**
- Commit `local.properties` or `google-services.json`
- Hardcode API keys in source code
- Share API keys via email, chat, or screenshots
- Use production keys in debug builds
- Commit anything with "key", "secret", or "password" in it

### API Key Restrictions

#### Google Maps API Key
In Google Cloud Console, configure:
- **Application restrictions**: Android apps
- **Package name**: `com.example.roadtripcompanion`
- **SHA-1 certificate fingerprint**: Add your debug and release keystore fingerprints
- **API restrictions**: Enable only required APIs:
  - Maps SDK for Android
  - Places API

#### Firebase
- Enable App Check for production
- Configure Firestore security rules
- Use Authentication for user management

### If a Key is Exposed

If you accidentally commit an API key:

1. **Immediately regenerate** the exposed key in Google Cloud Console
2. **Remove from git history**:
   ```bash
   # Using git filter-branch (not recommended for large repos)
   git filter-branch --force --index-filter \
     'git rm --cached --ignore-unmatch local.properties app/google-services.json' \
     --prune-empty --tag-name-filter cat -- --all
   
   # Clean up
   rm -rf .git/refs/original/
   git reflog expire --expire=now --all
   git gc --prune=now --aggressive
   
   # Force push
   git push --force origin main
   ```

3. **Update your local configuration** with new keys
4. **Notify team members** to pull the updated repository

## Dependencies

Keep dependencies up to date to patch security vulnerabilities:

```bash
# Check for outdated dependencies
./gradlew dependencyUpdates

# Update Gradle wrapper
./gradlew wrapper --gradle-version=latest
```

## Code Review Checklist

Before merging:
- [ ] No hardcoded credentials or API keys
- [ ] No sensitive files in commit
- [ ] `.gitignore` properly configured
- [ ] No logging of sensitive information
- [ ] Dependencies are up to date
- [ ] Proper error handling (no sensitive data in error messages)

## Production Deployment

For production releases:
1. Use release-specific API keys
2. Enable ProGuard/R8 obfuscation
3. Sign APK with release keystore
4. Store release keystore securely (never commit)
5. Enable Firebase App Check
6. Review Firestore security rules
7. Enable API key billing alerts

## Additional Resources

- [Google Maps API Security Best Practices](https://developers.google.com/maps/api-security-best-practices)
- [Firebase Security Documentation](https://firebase.google.com/docs/projects/api-keys)
- [Android App Security](https://developer.android.com/topic/security/best-practices)
