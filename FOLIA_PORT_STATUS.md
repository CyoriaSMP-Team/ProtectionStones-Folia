# ProtectionStones-Folia port status — 2026-10-17

**WIP / NOT VALIDATED. DO NOT DEPLOY TO PRODUCTION.**

Direct official fork of espidev/ProtectionStones. Manual port targeting Folia 1.21.11. No auto-updates or auto-deployment.

Changes: Paper 1.21.11 API, entity/region/global/async schedulers, reworked block and player workflows, teleports, region merge and admin commands, player UUID cache and region-state concurrency controls.

Validation: `git diff --check` passed. No direct legacy Bukkit scheduler uses remain in Java source. Maven could not finish compilation because dependency/plugin resolution failed in the restricted coding environment. No full build, full test suite, or live multi-region integration test was completed.

Remaining safety issues: async PlaceholderAPI integration returns empty values for unsafe reads. Cross-region piston, bucket and explosion events and complex WorldGuard region mutations require deeper ownership-safe design/testing. `folia-supported: true` is intentionally withheld until proven safe.

This is a source development branch, not a plugin release.
