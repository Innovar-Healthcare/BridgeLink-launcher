# bridgelink-launcher
An open source Admin Launcher for BridgeLink (and OSS Mirth Connect)

## MacOS Specific Instructions
Because the application is not signed by Apple, you may get a security warning and have to manually override your security settings to grant an exception to the launcher.

**Known Issue:**
If you extract the BridgeLink Launcher application straight to your Downloads folder, it will give you an error about "Read-Only Filesystem" when you try to save an entry.

To prevent this, you can do one of the following:
- Move the application to a different folder, such as /Applications or ~/Applications
- Create a new folder inside downloads then move the application there
- Enable All Applications in MacOS Gatekeeper by following these steps:
  * Open up System Settings
  * In System Settings, navigate to "Privacy & Security". Leave Window Open in the Background
  * Open up Terminal (as separate window). DO NOT CLOSE System Settings
  * In Terminal, run "sudo spctl --master-disable" --> Type Password --> Click Enter
  * In System Settings, navigate out of "Privacy & Security" Page (For Example -- Click on "Lockscreen"), then navigate back to "Privacy & Security"
  * In System Settings --> Privacy & Security Page --> Scroll Down to bottom --> Select "Allow Application From" --> Select "Anywhere" (the option will now appear) --> Type Password
Completed


## License

This project is licensed under the Mozilla Public License 2.0 (MPL-2.0). 

You are free to use, modify, and distribute this software under the terms of the MPL-2.0 license. This license requires that if you distribute modified versions of this software, you must also make the source code of those modifications available under the MPL-2.0.

For full details, see the [LICENSE](LICENSE) file or visit the [MPL-2.0 documentation](https://www.mozilla.org/en-US/MPL/2.0/).
