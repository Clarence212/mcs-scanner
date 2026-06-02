# Minecraft Server Port Scanner

A multi-threaded Minecraft server port scanner and bot joiner that runs in Powershell and its own GUI. It scans a range of ports on a target IP to find online Minecraft servers, retrieves their status, tests online/offline mode by joining, and logs the results.

## Feats

- **Multi-threaded Scanning**: Configurable speeds from Medium to Dangerous.
- **Server Status Retrieval**: Queries servers for MOTD, online player count, and protocol version.
- **Bot Join Simulation**: Attempts a full connection handshake to determine if a server is running in online mode (authenticated) or offline mode.
- **Logs**: Automatically exports found servers to time-stamped text files.
 <img width="864" height="383" alt="Screenshot 2026-06-02 212704" src="https://github.com/user-attachments/assets/0097f66b-190b-4117-a952-356d549c00cd" />

## Guide
- **IP** - This is where you enter the target server’s IP. If the server is hosted, you might see more results since multiple instances can run on the same address.
- **START PORT** - Defines the first port the scan will check. tho some ports can range from 0 to 65535, minecraft servers sometimes found in higher ranges like 20000-30000, but usually its around 25565.
- **ONLINE SERVERS ONLY** - When enabled, the results will only include servers that responded successfully. If turned off, both active and inactive results will be shown, this can make the log file more cluttered.
- **SCAN RANGE** - Controls how many ports will be checked starting from the initial port. Larger ranges increase scan time proportionally.
- **SPEED SETTINGS** - Adjusts how quickly the scan runs. Higher speeds may increase CPU usage, and the fastest mode can reach very high request rates depending on your system performance.

<img width="451" height="336" alt="image" src="https://github.com/user-attachments/assets/56d1249d-5361-464d-851d-7b81448d31c3" />


  



## Requirements

- Java Development Kit (JDK) 8 or higher.



