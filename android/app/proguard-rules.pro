# R8 rules for the release build.
#
# Room, Compose, lifecycle and the Kotlin standard library ship their own
# consumer rules, and nothing in MacroDime uses reflection or serialisation by
# name, so no keep rules are needed. Enum raw values are explicit strings, not
# names, which is why renaming an enum during shrinking cannot change what the
# database means.
