# liquibase

Build liquibase to native image
JDBC Driver: postgresql

### Release process of project

Create new release run
```bash
mvn semver-release:release-create
```

Create new patch branch run
```bash
mvn semver-release:patch-create -DpatchVersion=X.X.0
```


