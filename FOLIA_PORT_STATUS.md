# ProtectionStones-Folia port status — 2026-10-17

**Experimental Folia prerelease `v2.10.7-folia.1`. Startup and the basic claim lifecycle passed on Folia 1.21.11; multi-region safety is not yet verified.**

Direct official fork of espidev/ProtectionStones. Manual port targeting Folia 1.21.11. No auto-updates or auto-deployment.

Changes: Paper 1.21.11 API, entity/region/global/async schedulers, reworked block and player workflows, teleports, region merge and admin commands, player UUID cache and region-state concurrency controls.

Validation: GitHub Actions run [39060624317](https://github.com/CyoriaSMP-Team/ProtectionStones-Folia/actions/runs/39060624317) passed on Java 25 at commit `b0646c3cfef20ea7be712e97d963f84fefa7e2ee`. The build artifact was installed on CyoriaSMP Folia 1.21.11 and the server reached online state with ProtectionStones enabled. An isolated Folia 1.21.11 sandbox using the same JAR passed: `/ps get 25`, claim placement and WorldGuard region listing; a visitor's block placement and break were denied and the blocks remained unchanged; the owner used `/ps unclaim`, which removed the block and region. The source declares `folia-supported: true` to permit loading; the marker is not a safety certification.

Remaining safety issues: async PlaceholderAPI integration returns empty values for unsafe reads. Cross-region piston, bucket and explosion events, complex WorldGuard region mutations, and multi-region behavior require deeper ownership-safe design/testing. The live server startup and isolated claim test do not establish that existing player regions or every gameplay path are safe.

The experimental prerelease is [v2.10.7-folia.1](https://github.com/CyoriaSMP-Team/ProtectionStones-Folia/releases/tag/v2.10.7-folia.1). Do not publish a stable release until multi-region behavior and the remaining safety paths are verified.
