source "https://rubygems.org"

# fastlane automates building/uploading the Play Store release (see fastlane/Fastfile and
# release.yml's optional "Upload to Play Store" step). No Gemfile.lock is committed here since
# there's no Ruby installed in this project's usual dev environment to generate one - CI resolves
# a fresh one on every run instead (see release.yml's `bundler-cache: true`). If you do have Ruby
# available locally, running `bundle lock` once and committing the result makes CI both faster
# (cache hit) and fully reproducible, and lets Renovate (renovate.json) track fastlane's version
# like it does every other dependency here.
gem "fastlane"

