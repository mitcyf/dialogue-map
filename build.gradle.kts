plugins {
	java
}

group = "mit.cyf"
version = "0.1.0-SNAPSHOT"
val pluginVersion = version.toString()

java {
	toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
	testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
	testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks {
	compileJava {
		options.release = 21
		options.encoding = "UTF-8"
	}

	test {
		useJUnitPlatform()
	}

	processResources {
		filesMatching("plugin.yml") {
			expand("version" to pluginVersion)
		}
	}

	register<Copy>("deployPlugin") {
		group = "development"
		description = "Builds the plugin and installs it into the local map_testing server."
		dependsOn(jar)
		from(jar)
		into(layout.projectDirectory.dir("../plugins"))
	}

}
