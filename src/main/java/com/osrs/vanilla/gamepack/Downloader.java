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

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class Downloader
{
	@RequiredArgsConstructor
	@Getter
	private enum Option
	{
		HELP("help", "Display help."),
		JAV_CONFIG("javconfig", "The jav config url."),
		GAMEPACK("gamepack", "The gamepack toString."),
		PROPERTIES("properties", "The jav config properties."),
		REVISION("revision", "The gamepack revision."),
		SIZE("size", "The gamepack size."),
		SHA256("sha256", "The gamepack sha256 hash."),
		JAR_DOWNLOAD("jardownload", "The gamepack jar download url."),
		SAVE("save", "Save the gamepack to desired output"),
		GENERATE_POM("pom", "Generate pom to desired output"),
		PUBLISH_TO_MAVEN_LOCAL("publish", "Publish artifact to maven local repository (/home/.m2/repository/net/runelite/rs/vanilla/revision/).");

		private final String option;
		private final String description;
	}

	private static void addPomOption(OptionParser parser, String optionName, String description)
	{
		parser.accepts(optionName, description)
			.requiredIf(Option.GENERATE_POM.getOption())
			.withRequiredArg()
			.ofType(String.class)
			.describedAs(optionName);
	}

	public static void main(String[] args) throws IOException
	{
		//args = new String[]{"--save", "out/vanilla-229.jar", "--pom", "out/vanilla-229.pom", "--groupId", "net.runelite.rs", "--artifactId", "vanilla", "--version", "229"};

		if (args.length == 0)
		{
			log.error("No program argument specified!");
			System.exit(0);
			return;
		}

		log.info("Args: {}", Arrays.toString(args));

		final GamePack gamepack = GamePack.getInstance();
		if (gamepack == null
			|| gamepack.getRevision() <= 0
			|| gamepack.getSize() <= 0
			|| gamepack.getJarDownload() == null
			|| !gamepack.getJarDownload().endsWith(".jar")
	                || gamepack.getSha256() == null)
		{
			throw new RuntimeException("Failed to load gamepack!");
		}

		final OptionParser parser = new OptionParser(false);
		parser.accepts(Option.HELP.getOption(), Option.HELP.getDescription()).forHelp();
		parser.accepts(Option.JAV_CONFIG.getOption(), Option.JAV_CONFIG.getDescription());
		parser.accepts(Option.GAMEPACK.getOption(), Option.GAMEPACK.getDescription());
		parser.accepts(Option.PROPERTIES.getOption(), Option.PROPERTIES.getDescription());
		parser.accepts(Option.REVISION.getOption(), Option.REVISION.getDescription());
		parser.accepts(Option.SIZE.getOption(), Option.SIZE.getDescription());
		parser.accepts(Option.SHA256.getOption(), Option.SHA256.getDescription());
		parser.accepts(Option.JAR_DOWNLOAD.getOption(), Option.JAR_DOWNLOAD.getDescription());
		parser.accepts(Option.SAVE.getOption(), Option.SAVE.getDescription())
			.withRequiredArg()
			.ofType(String.class)
			.describedAs("outputFile")
			.defaultsTo("/out/net/runelite/rs/vanilla/" + gamepack.getRevision() + "/vanilla-" + gamepack.getRevision() + ".jar");

		parser.accepts(Option.GENERATE_POM.getOption(), Option.GENERATE_POM.getDescription())
			.withRequiredArg()
			.ofType(String.class)
			.describedAs("outputFile")
			.defaultsTo("/out/net/runelite/rs/vanilla/" + gamepack.getRevision() + "/vanilla-" + gamepack.getRevision() + ".pom");
		addPomOption(parser, "groupId", "Group ID of the artifact, requires --pom");
		addPomOption(parser, "artifactId", "Artifact ID of the artifact, requires --pom");
		addPomOption(parser, "version", "Version of the artifact, requires --pom");

		parser.accepts(Option.PUBLISH_TO_MAVEN_LOCAL.getOption(), Option.PUBLISH_TO_MAVEN_LOCAL.getDescription());

		final OptionSet options = parser.parse(args);

		if (options.has(Option.HELP.getOption()))
		{
			parser.printHelpOn(System.out);
			System.exit(0);
		}

		if (options.has(Option.JAV_CONFIG.getOption()))
		{
			System.out.println(gamepack.getJavConfigUrl());
		}

		if (options.has(Option.GAMEPACK.getOption()))
		{
			System.out.println(gamepack.toString());
		}

		if (options.has(Option.PROPERTIES.getOption()))
		{
			System.out.println(gamepack.getJavConfigProperties());
		}

		if (options.has(Option.REVISION.getOption()))
		{
			System.out.println(gamepack.getRevision());
		}

		if (options.has(Option.SIZE.getOption()))
		{
			System.out.println(gamepack.getSize());
		}

		if (options.has(Option.SHA256.getOption()))
		{
			System.out.println(gamepack.getSha256());
		}

		if (options.has(Option.JAR_DOWNLOAD.getOption()))
		{
			System.out.println(gamepack.getJarDownload());
		}

		if (options.has(Option.SAVE.getOption()))
		{
			gamepack.saveJar(new File((String) options.valueOf(Option.SAVE.getOption())), true);
		}

		if (options.has(Option.GENERATE_POM.getOption()))
		{
			String groupId = (String) options.valueOf("groupId");
			String artifactId = (String) options.valueOf("artifactId");
			String version = (String) options.valueOf("version");
			String outputFile = (String) options.valueOf(Option.GENERATE_POM.getOption());
			gamepack.generatePom(groupId, artifactId, version, new File(outputFile));
		}

		if (options.has(Option.PUBLISH_TO_MAVEN_LOCAL.getOption()))
		{
			gamepack.publishToMavenLocal();
		}
	}
}
