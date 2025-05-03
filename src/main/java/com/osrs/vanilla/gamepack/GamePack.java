/*
 * Copyright (c) 2025, Melxin <https://github.com/melxin>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.osrs.vanilla.gamepack;

import com.google.common.base.Stopwatch;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.jsoup.Jsoup;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.jar.JarFile;

@Getter
@Slf4j
public class GamePack
{
	private static class Holder
	{
		private static final GamePack INSTANCE = new GamePack();
	}

	public static GamePack getInstance()
	{
		return GamePack.Holder.INSTANCE;
	}

	private final String javConfigUrl = "https://oldschool.config.runescape.com/jav_config.ws";
	private Properties javConfigProperties;
	private int revision;
	private String jarDownload;
	private byte[] gamepackJar;
	private int size;
	private String sha256;

	private GamePack()
	{
		try
		{
			this.javConfigProperties = new Properties();

			javConfigProperties.load(new StringReader(Jsoup.connect(javConfigUrl).get().wholeText()
				.replace("msg=", "msg_")
				.replace("param=", "param_")));

			if (javConfigProperties.isEmpty())
			{
				log.error("JavConfig properties are empty!");
				throw new RuntimeException("JavConfig properties are empty!");
			}

			this.revision = Integer.parseInt(javConfigProperties.getProperty("param_25"));

			this.jarDownload = javConfigProperties.getProperty("codebase") + javConfigProperties.getProperty("initial_jar");
			final byte[] gamepackJar = Jsoup
				.connect(jarDownload)
				.maxBodySize(0)
				.ignoreContentType(true)
				.execute()
				.bodyAsBytes();

			if (gamepackJar == null)
			{
				log.error("Failed to connect!");
				System.exit(-1);
				return;
			}

			this.gamepackJar = gamepackJar;
			this.size = gamepackJar.length;

			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(gamepackJar);
			final StringBuilder hexString = new StringBuilder();
			for (byte b : digest)
			{
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}

			this.sha256 = hexString.toString();
		}
		catch (IOException | NoSuchAlgorithmException e)
		{
			log.error("Failed to retrieve gamepack", e);
			System.exit(-1);
		}

		log.info("Revision: {}", revision);
		log.info("Sha256: {}", sha256);
		log.info("Size: {} bytes", size);
	}

	public void saveJar(File outputFile, boolean vanillaLastModified) throws IOException
	{
		if (gamepackJar == null || !outputFile.getName().endsWith(".jar"))
		{
			log.error(gamepackJar == null ? "Cannot save gamepack because jar is null" : "Output file should end with .jar");
			return;
		}

		File outputDir = outputFile.getParentFile();
		if (!outputDir.exists())
		{
			outputDir.mkdirs();
		}

		Files.write(outputFile.toPath(), gamepackJar, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		if (vanillaLastModified)
		{
			JarFile jarFile = new JarFile(outputFile);
			FileTime lastModifiedTime = jarFile.getJarEntry(javConfigProperties.getProperty("initial_class")).getLastModifiedTime();
			BasicFileAttributeView attributeView = Files.getFileAttributeView(outputFile.toPath(), BasicFileAttributeView.class);
			attributeView.setTimes(lastModifiedTime, lastModifiedTime, lastModifiedTime);
		}
		log.info("Vanilla gamepack jar: {}", outputFile.getAbsolutePath());
	}

	/**
	 * Generate pom used for vanilla gamepack artifact hosting
	 *
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @param outputFile
	 */
	public void generatePom(String groupId, String artifactId, String version, File outputFile)
	{
		if (!outputFile.getName().endsWith(".pom"))
		{
			log.error("Output file should end with .pom");
			return;
		}

		final Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setGroupId(groupId);
		model.setArtifactId(artifactId);
		model.setVersion(version);

		try (Writer writer = new FileWriter(outputFile))
		{
			MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
			mavenWriter.write(writer, model);
			log.info("Pom file generated: {} groupId: {} artifactId: {}, version: {}", outputFile.getAbsolutePath(), groupId, artifactId, version);
		}
		catch (IOException e)
		{
			log.error("Error generating POM file", e);
		}
	}

	public void publishToMavenLocal()
	{
		final Stopwatch stopwatch = Stopwatch.createStarted();
		try
		{
			File outputDir = Paths.get(System.getProperty("user.home"), ".m2/repository/net/runelite/rs/vanilla", String.valueOf(revision)).toFile();
			if (!outputDir.exists())
			{
				outputDir.mkdirs();
			}
			final File jarOut = new File(outputDir, "vanilla-" + revision + ".jar");
			final File pomOut = new File(outputDir, jarOut.getName().replace(".jar", ".pom"));
			this.saveJar(jarOut, true);
			this.generatePom("net.runelite.rs", "vanilla", String.valueOf(revision), pomOut);
		}
		catch (IOException e)
		{
			log.error("Publish to local maven failed", e);
		}
		log.info("Took: {}", stopwatch);
	}

	@Override
	public String toString()
	{
		return new StringBuilder().append("jav_config=").append(javConfigUrl).append("\n")
			.append("revision=").append(revision).append("\n")
			.append("sha256=").append(sha256).append("\n")
			.append("size=").append(size).append("\n")
			.append("jar_download=").append(jarDownload)
			.toString();
	}
}