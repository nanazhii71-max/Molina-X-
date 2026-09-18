package com.molinax.manager.terminal

import java.io.File

data class LinuxDistro(
    val id: String,
    val name: String,
    val description: String,
    val defaultRootfsUrl: String,
    val iconName: String
)

object DistroManager {

    val SUPPORTED_DISTROS = listOf(
        LinuxDistro(
            id = "debian",
            name = "Debian Linux (Bookworm)",
            description = "Solid, stable, extensive apt package repository.",
            defaultRootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/debian-bookworm-aarch64.tar.xz",
            iconName = "debian"
        ),
        LinuxDistro(
            id = "ubuntu",
            name = "Ubuntu Linux (24.04 LTS)",
            description = "Modern canonical distribution with snap/apt ecosystems.",
            defaultRootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/ubuntu-noble-aarch64.tar.xz",
            iconName = "ubuntu"
        ),
        LinuxDistro(
            id = "alpine",
            name = "Alpine Linux (3.20)",
            description = "Ultra-lightweight, musl & BusyBox based, minimal footprint.",
            defaultRootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/alpine-v3.20-aarch64.tar.xz",
            iconName = "alpine"
        ),
        LinuxDistro(
            id = "archlinux",
            name = "Arch Linux ARM",
            description = "Rolling release with cutting-edge pacman packages.",
            defaultRootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/archlinux-aarch64.tar.xz",
            iconName = "arch"
        )
    )

    fun getDistroInstallCommand(distroId: String): String {
        return "proot-distro install $distroId"
    }

    fun getDistroLoginCommand(distroId: String): String {
        return "proot-distro login $distroId"
    }

    fun isDistroInstalled(prefixDir: File, distroId: String): Boolean {
        val distroDir = File(prefixDir, "var/lib/proot-distro/installed-rootfs/$distroId")
        return distroDir.exists() && distroDir.isDirectory && distroDir.list()?.isNotEmpty() == true
    }
}
