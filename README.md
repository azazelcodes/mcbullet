<div align="center">
  
<img src="https://github.com/user-attachments/assets/40181e90-d280-4217-a55e-5c3576869aa9" width="500px" />

<h2> jBullet in Minecraft </h2>
<sub><sup>I published it before Sethbling did</sup></sub>

[![GitHub release](https://img.shields.io/github/v/release/azazelcodes/mcbullet?color=blue&label=release)]()
[![GitHub issues](https://img.shields.io/github/issues/azazelcodes/mcbullet?color=red)]()
[![GitHub stars](https://img.shields.io/github/stars/azazelcodes/mcbullet)]()
[![Static Badge](https://img.shields.io/badge/paper?logo=educative&logoColor=%231F2937&label=built%20with&color=%233B82F6&link=https%3A%2F%2Fpapermc.io%2Fsoftware%2Fpaper)]() <!-- why no work -->

Create a physics world, set a ground plane and spawn softbodies all from a PaperMC plugin!
</div>

## 🗺️ Content

- [<code>📦 Installation</code>](#-installation)
- [<code>📝 Commands</code>](#-commands)
- [<code>⚙️ Configuration</code>](#-configuration)

## 📦 Installation
There are two branches: the main branch and the development branch <br>
The main branch contains the releases, which can be installed like any other plugin and the stable source code. <br>
mcBullet **requires** running on a Paper or Paper derivative! <br>
The development branch contains unstable features *(not right now)* and has to be built on your machine using gradle. <br>
<br>
The physics world is created on server start.

## 📝 Commands
/bullethelp - displays the commands ingame <br>
<br>
/physcount | getbullets | howmany - shows the number of physics objects <br>
/killall | killphys - kills all physics object *(including the ground)* <br>
<br>
/timemult | delta | timestep <br>
﹕get - shows the time multiplier <br>
╰ set <multiplier> - sets the time multipler (e.g. 1x, 2x, ...) *(may cause collision instability at higher multipliers)* <br>
/calc | calcstep | sub | substeps <br>
<br>
﹕get - shows the substeps that run per physics tick <br>
╰ set <multiplier> - sets the substeps that run per physics tick *(useless?)* <br>
<br>
/ground | g | sg | gs <loc1> <loc2> - spawns a ground at the specified position *(physics box with no gravity)* <br>
/spawnblock | sb <blocktype> - spawns a block with the specified material <br>
/spawnentity | se <entitytype> - spawns an entity of the specified type <br>

## 🔧 Configuration
Not implemented! Only configurable using [commands](#-commands)
