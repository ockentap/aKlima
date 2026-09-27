# aKlima keeps the ConnectLife JSON payloads as plain data classes / org.json calls —
# nothing here needs to survive R8 by name. Kept as a marker for future reflection use.
-keep class com.example.aklima.ClDevice { *; }
-keep class com.example.aklima.Tokens { *; }
