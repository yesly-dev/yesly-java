# Publishing to Maven Central

The artifact is `io.yesly:yesly-java`. Releases are published by
`.github/workflows/release.yml` when a `v*` tag is pushed: `mvn -P release
deploy` builds sources and javadoc jars, signs everything with GPG and uploads
through the Central Portal.

## One-time setup

1. **Central Portal account.** Sign up at <https://central.sonatype.com> with
   a shared address.
2. **Namespace.** *Namespaces* → *Add namespace*. The `groupId` must be a
   domain you control, reversed. Prove it with the DNS TXT record the portal
   shows.
   - `io.yesly` needs the domain **yesly.io**, which is not bought yet. Until
     it is, either wait, or use `dev.yesly` (TXT record on yesly.dev, in
     Route 53 through `yesly-infra`).
   - If you switch to `dev.yesly`, change `<groupId>` in `pom.xml`, the README
     and the Java line in `src/lib/sdks.ts` in yesly-web together.
   - Add the TXT record with Terraform in `global/dns`. You can remove it once
     the portal shows the namespace as verified.
3. **Signing key.** Central requires every file to be signed.

   ```bash
   gpg --quick-generate-key "Yesly Releases <releases@yesly.dev>" ed25519 sign 3y
   gpg --keyserver keys.openpgp.org --send-keys <KEY ID>
   gpg --armor --export-secret-keys <KEY ID>     # value for MAVEN_GPG_KEY
   ```

   keys.openpgp.org emails a link to confirm the address. Keep the key and its
   passphrase in the team's password manager.
4. **Portal token.** *Account* → *Generate User Token*. It gives a username
   and a password; they are not your login.
5. **GitHub.** In the repo, *Settings* → *Environments* → create
   `maven-central`, then add these environment secrets:
   - `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`: the token from step 4.
   - `MAVEN_GPG_KEY`: the armored private key from step 3.
   - `MAVEN_GPG_PASSPHRASE`: its passphrase.

   Set them from your own terminal, so they never pass through anyone else:

   ```bash
   gh secret set MAVEN_GPG_KEY --env maven-central --repo yesly-dev/yesly-java < private-key.asc
   gh secret set MAVEN_GPG_PASSPHRASE --env maven-central --repo yesly-dev/yesly-java
   ```

The repo can stay private; Central only checks the POM. The POM lists the
GitHub URL, so make it public before announcing the library.

## Each release

1. Bump `<version>` in `pom.xml` and `VERSION` in
   `src/main/java/io/yesly/Version.java`.
2. Merge to `main`.
3. Tag and push:

   ```bash
   git tag v0.2.0 && git push origin v0.2.0
   ```

The workflow checks the tag against `pom.xml`, then deploys and waits until
Central says *published*. Search for it at <https://central.sonatype.com>
first; Maven and Gradle can take about 30 minutes more to see it.

## If something goes wrong

- Nothing on Maven Central can be deleted or replaced. Fix forward with a new
  version.
- If validation fails (missing signature, javadoc or POM field), the
  deployment stays in the portal under *Deployments* with the reason. Drop it
  there and tag again after the fix.
