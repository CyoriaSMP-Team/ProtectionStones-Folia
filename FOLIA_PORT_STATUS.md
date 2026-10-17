# ProtectionStones-Folia port status — 2026-10-17

**Experimental Folia build. Startup and claim flows are being tested on CyoriaSMP Folia 1.21.11; multi-region safety is not yet verified.**

Direct official fork of espidev/ProtectionStones. Manual port targeting Folia 1.21.11. No auto-updates or auto-deployment.

Changes: Paper 1.21.11 API, entity/region/global/async schedulers, reworked block and player workflows, teleports, region merge and admin commands, player UUID cache and region-state concurrency controls.

Validation before this run: the GitHub Actions development build at commit `6b8b100` passed on Java 25 and uploaded an artifact. No direct legacy Bukkit scheduler uses remain in Java source. The prior status was stale about build availability. This source now declares `folia-supported: true` to permit the authorized iterative live test; the marker is not a safety certification.

Remaining safety issues: async PlaceholderAPI integration returns empty values for unsafe reads. Cross-region piston, bucket and explosion events and complex WorldGuard region mutations require deeper ownership-safe design/testing. The Folia metadata is enabled only to permit iterative testing; it is not a claim that these paths are safe.

Do not publish a stable release until claim creation, removal, protection checks, and multi-region behavior are verified. Publish only an experimental prerelease if startup and basic claim behavior pass, with these limits stated.
