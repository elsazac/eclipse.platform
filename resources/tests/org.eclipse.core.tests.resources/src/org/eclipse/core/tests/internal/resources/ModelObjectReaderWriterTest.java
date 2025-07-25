/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alexander Kurtakov <akurtako@redhat.com> - Bug 459343
 *     Mickael Istria (Red Hat Inc.) - Bug 488938
 *******************************************************************************/
package org.eclipse.core.tests.internal.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.harness.FileSystemHelper.getRandomLocation;
import static org.eclipse.core.tests.harness.FileSystemHelper.getTempDir;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInputStream;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;
import static org.eclipse.core.tests.resources.ResourceTestUtil.removeFromFileSystem;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.internal.events.BuildCommand;
import org.eclipse.core.internal.resources.LinkDescription;
import org.eclipse.core.internal.resources.LocalMetaArea;
import org.eclipse.core.internal.resources.ModelObjectWriter;
import org.eclipse.core.internal.resources.Project;
import org.eclipse.core.internal.resources.ProjectDescription;
import org.eclipse.core.internal.resources.ProjectDescriptionReader;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Platform.OS;
import org.eclipse.core.tests.resources.WorkspaceTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.InputSource;

public class ModelObjectReaderWriterTest {

	@Rule
	public WorkspaceTestRule workspaceRule = new WorkspaceTestRule();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	static final IPath LONG_LOCATION = IPath.fromOSString("/eclipse/dev/i0218/eclipse/pffds/fds//fds///fdsfsdfsd///fdsfdsf/fsdfsdfsd/lugi/dsds/fsd//f/ffdsfdsf/fsdfdsfsd/fds//fdsfdsfdsf/fdsfdsfds/fdsfdsfdsf/fdsfdsfdsds/ns/org.eclipse.help.ui_2.1.0/contexts.xml").setDevice(OS.isWindows() ? "D:" : null);
	static final URI LONG_LOCATION_URI = LONG_LOCATION.toFile().toURI();
	private static final String PATH_STRING = IPath.fromOSString("/abc/def").setDevice(OS.isWindows() ? "D:" : null).toString();

	private HashMap<String, ProjectDescription> buildBaselineDescriptors() {
		HashMap<String, ProjectDescription> result = new HashMap<>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		ProjectDescription desc = new ProjectDescription();
		desc.setName("abc.project");
		ICommand[] commands = new ICommand[1];
		commands[0] = desc.newCommand();
		commands[0].setBuilderName("org.eclipse.jdt.core.javabuilder");
		desc.setBuildSpec(commands);
		String[] natures = new String[1];
		natures[0] = "org.eclipse.jdt.core.javanature";
		desc.setNatureIds(natures);
		HashMap<IPath, LinkDescription> linkMap = new HashMap<>();
		LinkDescription link = createLinkDescription("newLink", IResource.FOLDER, "d:/abc/def");
		linkMap.put(link.getProjectRelativePath(), link);
		desc.setLinkDescriptions(linkMap);
		result.put(desc.getName(), desc);
		commands = null;
		natures = null;
		link = null;
		linkMap = null;
		desc = null;

		desc = new ProjectDescription();
		desc.setName("def.project");
		commands = new ICommand[1];
		commands[0] = desc.newCommand();
		commands[0].setBuilderName("org.eclipse.jdt.core.javabuilder");
		desc.setBuildSpec(commands);
		natures = new String[1];
		natures[0] = "org.eclipse.jdt.core.javanature";
		desc.setNatureIds(natures);
		linkMap = new HashMap<>();
		link = createLinkDescription("newLink", IResource.FOLDER, "d:/abc/def");
		linkMap.put(link.getProjectRelativePath(), link);
		link = createLinkDescription("link2", IResource.FOLDER, "d:/abc");
		linkMap.put(link.getProjectRelativePath(), link);
		link = createLinkDescription("link3", IResource.FOLDER, "d:/abc/def/ghi");
		linkMap.put(link.getProjectRelativePath(), link);
		link = createLinkDescription("link4", IResource.FILE, "d:/abc/def/afile.txt");
		linkMap.put(link.getProjectRelativePath(), link);
		desc.setLinkDescriptions(linkMap);
		result.put(desc.getName(), desc);
		commands = null;
		natures = null;
		link = null;
		linkMap = null;
		desc = null;

		desc = new ProjectDescription();
		desc.setName("org.apache.lucene.project");
		IProject[] refProjects = new Project[2];
		refProjects[0] = root.getProject("org.eclipse.core.boot");
		refProjects[1] = root.getProject("org.eclipse.core.runtime");
		desc.setReferencedProjects(refProjects);
		commands = new ICommand[3];
		commands[0] = desc.newCommand();
		commands[0].setBuilderName("org.eclipse.jdt.core.javabuilder");
		commands[1] = desc.newCommand();
		commands[1].setBuilderName("org.eclipse.pde.ManifestBuilder");
		commands[2] = desc.newCommand();
		commands[2].setBuilderName("org.eclipse.pde.SchemaBuilder");
		desc.setBuildSpec(commands);
		natures = new String[2];
		natures[0] = "org.eclipse.jdt.core.javanature";
		natures[1] = "org.eclipse.pde.PluginNature";
		desc.setNatureIds(natures);
		result.put(desc.getName(), desc);
		refProjects = null;
		commands = null;
		natures = null;
		desc = null;

		desc = new ProjectDescription();
		desc.setName("org.eclipse.ant.core.project");
		refProjects = new Project[4];
		refProjects[0] = root.getProject("org.apache.ant");
		refProjects[1] = root.getProject("org.apache.xerces");
		refProjects[2] = root.getProject("org.eclipse.core.boot");
		refProjects[3] = root.getProject("org.eclipse.core.runtime");
		desc.setReferencedProjects(refProjects);
		commands = new ICommand[2];
		commands[0] = desc.newCommand();
		commands[0].setBuilderName("org.eclipse.jdt.core.javabuilder");
		commands[1] = desc.newCommand();
		commands[1].setBuilderName("org.eclipse.ui.externaltools.ExternalToolBuilder");
		HashMap<String, String> argMap = new HashMap<>();
		argMap.put("!{tool_show_log}", "true");
		argMap.put("!{tool_refresh}", "${none}");
		argMap.put("!{tool_name}", "org.eclipse.ant.core extra builder");
		argMap.put("!{tool_dir}", "");
		argMap.put("!{tool_args}", "-DbuildType=${build_type}");
		argMap.put("!{tool_loc}", "${workspace_loc:/org.eclipse.ant.core/scripts/buildExtraJAR.xml}");
		argMap.put("!{tool_type}", "org.eclipse.ui.externaltools.type.ant");
		commands[1].setArguments(argMap);
		desc.setBuildSpec(commands);
		natures = new String[1];
		natures[0] = "org.eclipse.jdt.core.javanature";
		desc.setNatureIds(natures);
		result.put(desc.getName(), desc);
		refProjects = null;
		commands = null;
		natures = null;
		desc = null;

		return result;
	}

	private void compareBuildSpecs(ICommand[] commands, ICommand[] commands2) {
		// ASSUMPTION:  commands and commands2 are non-null
		assertThat(commands).as("compare number of commands").hasSameSizeAs(commands2);
		for (int i = 0; i < commands.length; i++) {
			assertThat(commands[i].getBuilderName()).as("compare names of builders at index %s", i)
					.isEqualTo(commands2[i].getBuilderName());
			Map<String, String> args = commands[i].getArguments();
			Map<String, String> args2 = commands2[i].getArguments();
			assertThat(args.entrySet()).as("compare number of arguments for builder at index %s", i)
					.hasSize(args2.size());
			for (Entry<String, String> entry : args.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				String value2 = args2.get(key);
				if (value == null) {
					assertThat(value2).as("value for key '%s'", key).isNull();
				} else {
					assertThat(args.get(key)).as("compare values for key: %s", key).isEqualTo(args2.get(key));
				}
			}
		}
	}

	private void compareProjectDescriptions(int errorTag, ProjectDescription description, ProjectDescription description2) {
		assertThat(description.getName()).isEqualTo(description2.getName());
		String comment = description.getComment();
		if (comment == null) {
			// The old reader previously returned null for an empty comment.  We
			// are changing this so it now returns an empty string.
			assertThat(description2.getComment()).isEmpty();
		} else {
			assertThat(description.getComment()).isEqualTo(description2.getComment());
		}

		IProject[] projects = description.getReferencedProjects();
		IProject[] projects2 = description2.getReferencedProjects();
		compareProjects(projects, projects2);

		ICommand[] commands = description.getBuildSpec();
		ICommand[] commands2 = description2.getBuildSpec();
		compareBuildSpecs(commands, commands2);

		String[] natures = description.getNatureIds();
		String[] natures2 = description2.getNatureIds();
		assertThat(natures).isEqualTo(natures2);

		HashMap<IPath, LinkDescription> links = description.getLinks();
		HashMap<IPath, LinkDescription> links2 = description2.getLinks();
		assertThat(links).isEqualTo(links2);
	}

	private void compareProjects(IProject[] projects, IProject[] projects2) {
		// ASSUMPTION:  projects and projects2 are non-null
		assertThat(projects).as("compare number of projects").hasSameSizeAs(projects2);
		for (int i = 0; i < projects.length; i++) {
			assertThat(projects[i].getName()).as("compare names of projects at index %s", i)
					.isEqualTo(projects2[i].getName());
		}
	}

	protected boolean contains(Object key, Object[] array) {
		for (Object element : array) {
			if (key.equals(element)) {
				return true;
			}
		}
		return false;
	}

	private LinkDescription createLinkDescription(IPath path, int type, URI location) {
		LinkDescription result = new LinkDescription();
		result.setPath(path);
		result.setType(type);
		result.setLocationURI(location);
		return result;
	}

	private LinkDescription createLinkDescription(String path, int type, String location) {
		return createLinkDescription(IPath.fromOSString(path), type, uriFromPortableString(location));
	}

	private String getLongDescription() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<projectDescription>" + "<name>org.eclipse.help.ui</name>" + "<comment></comment>" + "<charset>UTF-8</charset>" + "	<projects>" + "	<project>org.eclipse.core.boot</project>" + "	<project>org.eclipse.core.resources</project>" + "	<project>org.eclipse.core.runtime</project>" + "	<project>org.eclipse.help</project>" + "	<project>org.eclipse.help.appserver</project>" + "	<project>org.eclipse.search</project>" + "	<project>org.eclipse.ui</project>" + "	</projects>" + "	<buildSpec>" + "	<buildCommand>" + "	<name>org.eclipse.jdt.core.javabuilder</name>" + "	<arguments>" + "	</arguments>" + "	</buildCommand>" + "	<buildCommand>" + "	<name>org.eclipse.pde.ManifestBuilder</name>" + "	<arguments>"
				+ "	</arguments>" + "	</buildCommand>" + "	<buildCommand>" + "	<name>org.eclipse.pde.SchemaBuilder</name>" + "	<arguments>" + "	</arguments>" + "	</buildCommand>" + "	</buildSpec>" + "	<natures>" + "	<nature>org.eclipse.jdt.core.javanature</nature>" + "	<nature>org.eclipse.pde.PluginNature</nature>" + "	</natures>" + "	<linkedResources>" + "	<link>" + "	<name>contexts.xml</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>doc</name>" + "	<type>2</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>icons</name>" + "	<type>2</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>preferences.ini</name>"
				+ "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>.options</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>plugin.properties</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>plugin.xml</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>about.html</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "	<link>" + "	<name>helpworkbench.jar</name>" + "	<type>1</type>" + "	<location>" + LONG_LOCATION + "</location>" + "	</link>" + "</linkedResources>" + "</projectDescription>";
	}

	private String getLongDescriptionURI() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<projectDescription>" + "<name>org.eclipse.help.ui</name>" + "<comment></comment>" + "<charset>UTF-8</charset>" + "	<projects>" + "	<project>org.eclipse.core.boot</project>" + "	<project>org.eclipse.core.resources</project>" + "	<project>org.eclipse.core.runtime</project>" + "	<project>org.eclipse.help</project>" + "	<project>org.eclipse.help.appserver</project>" + "	<project>org.eclipse.search</project>" + "	<project>org.eclipse.ui</project>" + "	</projects>" + "	<buildSpec>" + "	<buildCommand>" + "	<name>org.eclipse.jdt.core.javabuilder</name>" + "	<arguments>" + "	</arguments>" + "	</buildCommand>" + "	<buildCommand>" + "	<name>org.eclipse.pde.ManifestBuilder</name>" + "	<arguments>"
				+ "	</arguments>" + "	</buildCommand>" + "	<buildCommand>" + "	<name>org.eclipse.pde.SchemaBuilder</name>" + "	<arguments>" + "	</arguments>" + "	</buildCommand>" + "	</buildSpec>" + "	<natures>" + "	<nature>org.eclipse.jdt.core.javanature</nature>" + "	<nature>org.eclipse.pde.PluginNature</nature>" + "	</natures>" + "	<linkedResources>" + "	<link>" + "	<name>contexts.xml</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>doc</name>" + "	<type>2</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>icons</name>" + "	<type>2</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>"
				+ "	<name>preferences.ini</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>.options</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>plugin.properties</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>plugin.xml</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>about.html</name>" + "	<type>1</type>" + "	<locationURI>" + LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "	<link>" + "	<name>helpworkbench.jar</name>" + "	<type>1</type>" + "	<locationURI>"
				+ LONG_LOCATION_URI + "</locationURI>" + "	</link>" + "</linkedResources>" + "</projectDescription>";
	}

	/**
	 * Reads and returns the project description stored in the given file store.
	 */
	private ProjectDescription readDescription(IFileStore store) throws CoreException, IOException {
		try (InputStream input = store.openInputStream(EFS.NONE, createTestMonitor())) {
			InputSource in = new InputSource(input);
			return new ProjectDescriptionReader(getWorkspace()).read(in);
		}
	}

	/**
	 * Verifies that project description file is written in a consistent way.
	 * (bug 177148)
	 */
	@Test
	public void testConsistentWrite() throws Throwable {
		String locationA = getTempDir().append("testPath1").toPortableString();
		String locationB = getTempDir().append("testPath1").toPortableString();
		String newline = System.lineSeparator();
		String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + newline + "<projectDescription>" + newline + "	<name>MyProjectDescription</name>" + newline + "	<comment></comment>" + newline + "	<projects>" + newline + "	</projects>" + newline + "	<buildSpec>" + newline + "		<buildCommand>" + newline + "			<name>MyCommand</name>" + newline + "			<arguments>" + newline + "				<dictionary>" + newline + "					<key>aA</key>" + newline + "					<value>2 x ARGH!</value>" + newline + "				</dictionary>" + newline + "				<dictionary>" + newline + "					<key>b</key>" + newline + "					<value>ARGH!</value>" + newline + "				</dictionary>" + newline
				+ "			</arguments>" + newline + "		</buildCommand>" + newline + "	</buildSpec>" + newline + "	<natures>" + newline + "	</natures>" + newline + "	<linkedResources>" + newline + "		<link>" + newline + "			<name>pathA</name>" + newline + "			<type>2</type>" + newline + "			<location>" + locationA + "</location>" + newline + "		</link>" + newline + "		<link>" + newline + "			<name>pathB</name>" + newline + "			<type>2</type>" + newline + "			<location>" + locationB + "</location>" + newline + "		</link>" + newline + "	</linkedResources>" + newline + "</projectDescription>" + newline;

		IFileStore tempStore = workspaceRule.getTempStore();
		URI location = tempStore.toURI();

		ProjectDescription description = new ProjectDescription();
		description.setLocationURI(location);
		description.setName("MyProjectDescription");
		HashMap<String, String> args = new HashMap<>(2);
		// key values are important
		args.put("b", "ARGH!");
		args.put("aA", "2 x ARGH!");
		ICommand[] commands = new ICommand[1];
		commands[0] = description.newCommand();
		commands[0].setBuilderName("MyCommand");
		commands[0].setArguments(args);
		description.setBuildSpec(commands);
		HashMap<IPath, LinkDescription> linkDescriptions = new HashMap<>(2);
		LinkDescription link = createLinkDescription("pathB", IResource.FOLDER, locationB);
		// key values are important
		linkDescriptions.put(link.getProjectRelativePath(), link);
		link = createLinkDescription("pathA", IResource.FOLDER, locationA);
		linkDescriptions.put(link.getProjectRelativePath(), link);
		description.setLinkDescriptions(linkDescriptions);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		new ModelObjectWriter().write(description, buffer, System.lineSeparator());
		String result = buffer.toString();

		// order of keys in serialized file should be exactly the same as expected
		assertThat(result).isEqualTo(expected);
	}

	@Test
	public void testLocalMetaAreaReadWriteNatures() throws CoreException, IOException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		LocalMetaArea area = new LocalMetaArea((Workspace) workspace);
		ProjectDescription description = new ProjectDescription();
		description.setName("Testme");
		String[] value = new String[] { "org.eclipse.jdt.core.javanature", "org.eclipse.pde.PluginNature" };
		description.setNatureIds(value);
		File file = folder.newFile();
		area.writeToFile(description, file);
		IProject proj = workspace.getRoot().getProject("tmpproject");
		ProjectDescription readme = new ProjectDescription();
		area.readFromFile(proj, readme, file);
		assertArrayEquals(value, readme.getNatureIds());
	}

	@Test
	public void testLocalMetaAreaReadWriteBuildSpec() throws CoreException, IOException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		LocalMetaArea area = new LocalMetaArea((Workspace) workspace);
		ProjectDescription description = new ProjectDescription();
		description.setName("Testme");
		BuildCommand buildCommand = new BuildCommand();
		buildCommand.setBuilderName("testBuilder");
		Map<String, String> args = Map.of("1", "2");
		buildCommand.setArguments(args);
		description.setBuildSpec(new ICommand[] { buildCommand });
		File file = folder.newFile();
		area.writeToFile(description, file);
		IProject proj = workspace.getRoot().getProject("tmpproject");
		ProjectDescription readme = new ProjectDescription();
		area.readFromFile(proj, readme, file);
		ICommand[] buildSpec = readme.getBuildSpec();
		assertEquals(1, buildSpec.length);
		ICommand read = buildSpec[0];
		assertEquals("testBuilder", read.getBuilderName());
		assertEquals(args, read.getArguments());
	}

	@Test
	public void testInvalidProjectDescription1() throws Throwable {
		String invalidProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<homeDescription>\n" + "	<name>abc</name>\n" + "	<comment></comment>\n" + "	<projects>\n" + "	</projects>\n" + "	<buildSpec>\n" + "		<buildCommand>\n" + "			<name>org.eclipse.jdt.core.javabuilder</name>\n" + "			<arguments>\n" + "			</arguments>\n" + "		</buildCommand>\n" + "	</buildSpec>\n" + "	<natures>\n" + "	<nature>org.eclipse.jdt.core.javanature</nature>\n" + "	</natures>\n" + "	<linkedResources>\n" + "		<link>\n" + "			<name>newLink</name>\n" + "			<type>2</type>\n" + "			<location>" + PATH_STRING + "</location>\n" + "		</link>\n" + "	</linkedResources>\n" + "</homeDescription>";

		IWorkspace workspace = getWorkspace();
		IPath root = workspace.getRoot().getLocation();
		IPath location = root.append("ModelObjectReaderWriterTest.txt");
		workspaceRule.deleteOnTearDown(location);

		ProjectDescriptionReader reader = new ProjectDescriptionReader(workspace);
		// Write out the project description file
		removeFromFileSystem(location.toFile());
		try (FileOutputStream output = new FileOutputStream(location.toFile())) {
			createInputStream(invalidProjectDescription).transferTo(output);
		}
		ProjectDescription projDesc = reader.read(location);
		assertThat(projDesc).isNull();
	}

	@Test
	public void testInvalidProjectDescription2() throws Throwable {
		String invalidProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "	<bogusname>abc</bogusname>\n" + "</projectDescription>";

		IFileStore store = workspaceRule.getTempStore();
		// Write out the project description file
		try (OutputStream output = store.openOutputStream(EFS.NONE, null)) {
			createInputStream(invalidProjectDescription).transferTo(output);
		}
		ProjectDescription projDesc = readDescription(store);
		assertThat(projDesc).isNotNull();
		assertThat(projDesc.getName()).isNull();
		assertThat(projDesc.getComment()).isEmpty();
		assertThat(projDesc.getLocationURI()).isNull();
		assertThat(projDesc.getReferencedProjects()).isEmpty();
		assertThat(projDesc.getNatureIds()).isEmpty();
		assertThat(projDesc.getBuildSpec()).isEmpty();
		assertThat(projDesc.getLinks()).isNull();
	}

	@Test
	public void testInvalidProjectDescription3() throws Throwable {
		String invalidProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "	<name>abc</name>\n" + "	<comment></comment>\n" + "	<projects>\n" + "	</projects>\n" + "	<buildSpec>\n" + "		<badBuildCommand>\n" + "			<name>org.eclipse.jdt.core.javabuilder</name>\n" + "			<arguments>\n" + "			</arguments>\n" + "		</badBuildCommand>\n" + "	</buildSpec>\n" + "</projectDescription>";

		IFileStore store = workspaceRule.getTempStore();
		// Write out the project description file
		try (OutputStream output = store.openOutputStream(EFS.NONE, null)) {
			createInputStream(invalidProjectDescription).transferTo(output);
		}

		ProjectDescription projDesc = readDescription(store);
		assertThat(projDesc).isNotNull();
		assertThat(projDesc.getName()).isEqualTo("abc");
		assertThat(projDesc.getComment()).isEmpty();
		assertThat(projDesc.getLocationURI()).isNull();
		assertThat(projDesc.getReferencedProjects()).isEmpty();
		assertThat(projDesc.getNatureIds()).isEmpty();
		assertThat(projDesc.getBuildSpec()).isEmpty();
		assertThat(projDesc.getLinks()).isNull();
	}

	@Test
	public void testInvalidProjectDescription4() throws Throwable {
		String invalidProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "	<name>abc</name>\n" + "	<comment></comment>\n" + "	<projects>\n" + "	</projects>\n" + "	<buildSpec>\n" + "	</buildSpec>\n" + "	<natures>\n" + "	</natures>\n" + "	<linkedResources>\n" + "		<link>\n" + "			<name>newLink</name>\n" + "			<type>foobar</type>\n" + "			<location>" + PATH_STRING + "</location>\n" + "		</link>\n" + "	</linkedResources>\n" + "</projectDescription>";

		IFileStore store = workspaceRule.getTempStore();
		// Write out the project description file
		try (OutputStream output = store.openOutputStream(EFS.NONE, null)) {
			createInputStream(invalidProjectDescription).transferTo(output);
		}
		ProjectDescription projDesc = readDescription(store);
		assertThat(projDesc).isNotNull();
		assertThat(projDesc.getName()).isEqualTo("abc");
		assertThat(projDesc.getComment()).isEmpty();
		assertThat(projDesc.getLocationURI()).isNull();
		assertThat(projDesc.getReferencedProjects()).isEmpty();
		assertThat(projDesc.getNatureIds()).isEmpty();
		assertThat(projDesc.getBuildSpec()).isEmpty();
		LinkDescription link = projDesc.getLinks().values().iterator().next();
		assertThat(link.getProjectRelativePath()).isEqualTo(IPath.fromOSString("newLink"));
		assertThat(URIUtil.toPath(link.getLocationURI()).toString()).isEqualTo(PATH_STRING);
	}

	/**
	 * Tests a project description with a very long local location for a linked resource.
	 */
	@Test
	public void testLongProjectDescription() throws Throwable {
		String longProjectDescription = getLongDescription();

		IPath location = getRandomLocation();
		workspaceRule.deleteOnTearDown(location);

		ProjectDescriptionReader reader = new ProjectDescriptionReader(getWorkspace());
		// Write out the project description file
		removeFromFileSystem(location.toFile());
		try (FileOutputStream output = new FileOutputStream(location.toFile())) {
			createInputStream(longProjectDescription).transferTo(output);
		}
		ProjectDescription projDesc = reader.read(location);
		removeFromFileSystem(location.toFile());
		for (LinkDescription link : projDesc.getLinks().values()) {
			assertThat(link.getLocationURI())
					.as("location URI for link with relative path: %s", link.getProjectRelativePath())
					.isEqualTo(LONG_LOCATION_URI);
		}
	}

	/**
	 * Tests a project description with a very long URI location for linked resource.
	 */
	@Test
	public void testLongProjectDescriptionURI() throws Throwable {
		String longProjectDescription = getLongDescriptionURI();
		IPath location = getRandomLocation();
		workspaceRule.deleteOnTearDown(location);

		ProjectDescriptionReader reader = new ProjectDescriptionReader(ResourcesPlugin.getWorkspace());
		// Write out the project description file
		removeFromFileSystem(location.toFile());
		try (FileOutputStream output = new FileOutputStream(location.toFile())) {
			createInputStream(longProjectDescription).transferTo(output);
		}
		ProjectDescription projDesc = reader.read(location);
		removeFromFileSystem(location.toFile());
		for (LinkDescription link : projDesc.getLinks().values()) {
			assertThat(link.getLocationURI())
					.as("location URI for link with relative path: %s", link.getProjectRelativePath())
					.isEqualTo(LONG_LOCATION_URI);
		}
	}

	@Test
	public void testMultiLineCharFields() throws Throwable {
		String multiLineProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "	<name>\n" + "      abc\n" + "   </name>\n" + "	<charset>\n" + "		ISO-8859-1\n" + "	</charset>\n" + "	<comment>This is the comment.</comment>\n" + "	<projects>\n" + "	   <project>\n" + "         org.eclipse.core.boot\n" + "      </project>\n" + "	</projects>\n" + "	<buildSpec>\n" + "		<buildCommand>\n" + "			<name>\n" + "              org.eclipse.jdt.core.javabuilder\n" + "           </name>\n" + "			<arguments>\n" + "              <key>thisIsTheKey</key>\n" + "              <value>thisIsTheValue</value>\n" + "			</arguments>\n" + "		</buildCommand>\n" + "	</buildSpec>\n" + "	<natures>\n" + "	   <nature>\n"
				+ "         org.eclipse.jdt.core.javanature\n" + "      </nature>\n" + "	</natures>\n" + "	<linkedResources>\n" + "		<link>\n" + "			<name>" + "newLink" + "</name>\n" + "			<type>\n" + "              2\n" + "           </type>\n" + "			<location>" + PATH_STRING + "</location>\n" + "		</link>\n" + "	</linkedResources>\n" + "</projectDescription>";

		String singleLineProjectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription>\n" + "	<name>abc</name>\n" + "	<charset>ISO-8859-1</charset>\n" + "	<comment>This is the comment.</comment>\n" + "	<projects>\n" + "	   <project>org.eclipse.core.boot</project>\n" + "	</projects>\n" + "	<buildSpec>\n" + "		<buildCommand>\n" + "			<name>org.eclipse.jdt.core.javabuilder</name>\n" + "			<arguments>\n" + "              <key>thisIsTheKey</key>\n" + "              <value>thisIsTheValue</value>\n" + "			</arguments>\n" + "		</buildCommand>\n" + "	</buildSpec>\n" + "	<natures>\n" + "	   <nature>org.eclipse.jdt.core.javanature</nature>\n" + "	</natures>\n" + "	<linkedResources>\n" + "		<link>\n"
				+ "			<name>newLink</name>\n" + "			<type>2</type>\n" + "			<location>" + PATH_STRING + "</location>\n" + "		</link>\n" + "	</linkedResources>\n" + "</projectDescription>";

		IWorkspace workspace = getWorkspace();
		IPath root = workspace.getRoot().getLocation();
		IPath multiLocation = root.append("multiLineTest.txt");
		workspaceRule.deleteOnTearDown(multiLocation);
		IPath singleLocation = root.append("singleLineTest.txt");
		workspaceRule.deleteOnTearDown(singleLocation);

		ProjectDescriptionReader reader = new ProjectDescriptionReader(workspace);
		// Write out the project description file
		removeFromFileSystem(multiLocation.toFile());
		removeFromFileSystem(singleLocation.toFile());
		try (FileOutputStream output = new FileOutputStream(multiLocation.toFile())) {
			createInputStream(multiLineProjectDescription).transferTo(output);
		}
		try (FileOutputStream output = new FileOutputStream(singleLocation.toFile())) {
			createInputStream(singleLineProjectDescription).transferTo(output);
		}
		ProjectDescription multiDesc = reader.read(multiLocation);
		ProjectDescription singleDesc = reader.read(singleLocation);
		compareProjectDescriptions(1, multiDesc, singleDesc);
	}

	@Test
	public void testMultipleProjectDescriptions() throws Throwable {
		URL whereToLook = Platform.getBundle("org.eclipse.core.tests.resources").getEntry("MultipleProjectTestFiles/");
		String[] members = {"abc.project", "def.project", "org.apache.lucene.project", "org.eclipse.ant.core.project"};
		HashMap<String, ProjectDescription> baselines = buildBaselineDescriptors();
		ProjectDescriptionReader reader = new ProjectDescriptionReader(getWorkspace());

		for (int i = 0; i < members.length; i++) {
			URL currentURL = null;
			currentURL = new URL(whereToLook, members[i]);
			try (InputStream is = currentURL.openStream()) {
				InputSource in = new InputSource(is);
				ProjectDescription description = reader.read(in);
				compareProjectDescriptions(i + 1, description, baselines.get(members[i]));
			}
		}
	}

	@Test
	public void testProjectDescription() throws Throwable {
		IFileStore tempStore = workspaceRule.getTempStore();
		URI location = tempStore.toURI();
		/* test write */
		ProjectDescription description = new ProjectDescription();
		description.setLocationURI(location);
		description.setName("MyProjectDescription");
		HashMap<String, String> args = new HashMap<>(3);
		args.put("ArgOne", "ARGH!");
		args.put("ArgTwo", "2 x ARGH!");
		args.put("NullArg", null);
		args.put("EmptyArg", "");
		ICommand[] commands = new ICommand[2];
		commands[0] = description.newCommand();
		commands[0].setBuilderName("MyCommand");
		commands[0].setArguments(args);
		commands[1] = description.newCommand();
		commands[1].setBuilderName("MyOtherCommand");
		commands[1].setArguments(args);
		description.setBuildSpec(commands);

		writeDescription(tempStore, description);

		/* test read */
		ProjectDescription description2 = readDescription(tempStore);
		assertThat(description.getName()).isEqualTo(description2.getName());
		assertThat(location).isEqualTo(description.getLocationURI());

		ICommand[] commands2 = description2.getBuildSpec();
		assertThat(commands2).hasSize(2).satisfiesExactly(first -> {
			assertThat(first.getBuilderName()).as("name").isEqualTo("MyCommand");
			assertThat(first.getArguments().get("ArgOne")).as("ArgOne").isEqualTo("ARGH!");
			assertThat(first.getArguments().get("ArgTwo")).as("ArgTwO").isEqualTo("2 x ARGH!");
			assertThat(first.getArguments().get("NullArg")).as("NullArg").isEmpty();
			assertThat(first.getArguments().get("EmptyArg")).as("EmptyArg").isEmpty();
			assertThat(first.getArguments().get("NullArg")).as("NullArg").isEmpty();
			assertThat(first.getArguments().get("EmptyArg")).as("EmptyArg").isEmpty();
		}, second -> {
			assertThat(second.getBuilderName()).as("name").isEqualTo("MyOtherCommand");
			assertThat(second.getArguments().get("ArgOne")).as("ArgOne").isEqualTo("ARGH!");
		});
	}

	@Test
	public void testProjectDescription2() throws Throwable {
		// Use ModelObject2 to read the project description

		/* initialize common objects */
		ModelObjectWriter writer = new ModelObjectWriter();
		ProjectDescriptionReader reader = new ProjectDescriptionReader(getWorkspace());
		IFileStore tempStore = workspaceRule.getTempStore();
		URI location = tempStore.toURI();
		/* test write */
		ProjectDescription description = new ProjectDescription();
		description.setLocationURI(location);
		description.setName("MyProjectDescription");
		HashMap<String, String> args = new HashMap<>(3);
		args.put("ArgOne", "ARGH!");
		ICommand[] commands = new ICommand[1];
		commands[0] = description.newCommand();
		commands[0].setBuilderName("MyCommand");
		commands[0].setArguments(args);
		description.setBuildSpec(commands);
		String comment = "Now is the time for all good men to come to the aid of the party.  Now is the time for all good men to come to the aid of the party.  Now is the time for all good men to come to the aid of the party.";
		description.setComment(comment);
		IProject[] refProjects = new IProject[3];
		refProjects[0] = ResourcesPlugin.getWorkspace().getRoot().getProject("org.eclipse.core.runtime");
		refProjects[1] = ResourcesPlugin.getWorkspace().getRoot().getProject("org.eclipse.core.boot");
		refProjects[2] = ResourcesPlugin.getWorkspace().getRoot().getProject("org.eclipse.core.resources");
		description.setReferencedProjects(refProjects);

		try (OutputStream output = tempStore.openOutputStream(EFS.NONE, createTestMonitor())) {
			writer.write(description, output, System.lineSeparator());
		}

		/* test read */
		ProjectDescription description2;
		try (InputStream input = tempStore.openInputStream(EFS.NONE, createTestMonitor())) {
			InputSource in = new InputSource(input);
			description2 = reader.read(in);
		}

		assertThat(description.getName()).isEqualTo(description2.getName());
		assertThat(location).isEqualTo(description.getLocationURI());

		ICommand[] commands2 = description2.getBuildSpec();
		assertThat(commands2).hasSize(1).satisfiesExactly(command -> {
			assertThat(command.getBuilderName()).as("name").isEqualTo("MyCommand");
			assertThat(command.getArguments().get("ArgOne")).as("ArgOne").isEqualTo("ARGH!");
		});

		assertThat(description.getComment()).isEqualTo(description2.getComment());

		IProject[] ref = description.getReferencedProjects();
		IProject[] ref2 = description2.getReferencedProjects();
		assertThat(ref2).hasSize(3).satisfiesExactly(
				first -> assertThat(first.getName()).isEqualTo(ref[0].getName()),
				second -> assertThat(second.getName()).isEqualTo(ref[1].getName()),
				third -> assertThat(third.getName()).isEqualTo(ref[2].getName()));
	}

	// see bug 274437
	@Test
	public void testProjectDescription3() throws Throwable {
		IFileStore tempStore = workspaceRule.getTempStore();
		URI location = tempStore.toURI();
		/* test write */
		ProjectDescription description = new ProjectDescription();
		description.setLocationURI(location);
		description.setName("MyProjectDescription");
		ICommand[] commands = new ICommand[1];
		commands[0] = description.newCommand();
		commands[0].setBuilderName("MyCommand");
		commands[0].setArguments(null);
		description.setBuildSpec(commands);

		writeDescription(tempStore, description);

		/* test read */
		ProjectDescription description2 = readDescription(tempStore);
		assertThat(description.getName()).isEqualTo(description2.getName());
		assertThat(description.getLocationURI()).isEqualTo(location);

		ICommand[] commands2 = description2.getBuildSpec();
		assertThat(commands2).hasSize(1).satisfiesExactly(command -> {
			assertThat(command.getBuilderName()).as("name").isEqualTo("MyCommand");
			assertThat(command.getArguments()).as("arguments").isEmpty();
		});
	}

	@Test
	public void testProjectDescriptionWithSpaces() throws Throwable {
		IFileStore store = workspaceRule.getTempStore();
		IPath path = IPath.fromOSString("link");
		URI location = store.toURI();
		URI locationWithSpaces = store.getChild("With some spaces").toURI();
		/* test write */
		ProjectDescription description = new ProjectDescription();
		description.setLocationURI(location);
		description.setName("MyProjectDescription");
		description.setLinkLocation(path, createLinkDescription(path, IResource.FOLDER, locationWithSpaces));

		writeDescription(store, description);

		/* test read */
		ProjectDescription description2 = readDescription(store);
		assertThat(description.getName()).isEqualTo(description2.getName());
		assertThat(description.getLocationURI()).isEqualTo(location);
		assertThat(description2.getLinkLocationURI(path)).isEqualTo(locationWithSpaces);
	}

	protected URI uriFromPortableString(String pathString) {
		return IPath.fromPortableString(pathString).toFile().toURI();
	}

	/**
	 * Writes a project description to a file store
	 */
	private void writeDescription(IFileStore store, ProjectDescription description) throws IOException, CoreException {
		try (OutputStream output = store.openOutputStream(EFS.NONE, createTestMonitor())) {
			new ModelObjectWriter().write(description, output, System.lineSeparator());
		}
	}

	// Regression for Bug 300669
	@Test
	public void testProjectDescriptionWithFiltersAndNullProject() throws Exception {
		String projectDescription = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
				"<projectDescription>\n" + //
				"	<name>rome_dfw</name>\n" + //
				"		<comment></comment>\n" + //
				"	<projects>\n" + //
				"	</projects>\n" + //
				"	<linkedResources>\n" + //
				"		<link>\n" + //
				"			<name>OcdTargetPlugin</name>\n" + //
				"			<type>2</type>\n" + //
				"			<location>M:/lcruaud.dfw-main-validation/HSI_api/OcdTargetPlugin</location>\n" + //
				"		</link>\n" + //
				"	</linkedResources>\n" + //
				"	<filteredResources>\n" + //
				"		<filter>\n" + //
				"			<id>1264174785480</id>\n" + //
				"			<name></name>\n" + //
				"			<type>22</type>\n" + //
				"			<matcher>\n" + //
				"				<id>org.eclipse.ui.ide.patternFilterMatcher</id>\n" + //
				"				<arguments>*:*</arguments>\n" + //
				"			</matcher>\n" + //
				"		</filter>\n" + //
				"	</filteredResources>\n" + //
				"</projectDescription>\n";

		IPath root = getWorkspace().getRoot().getLocation();
		IPath location = root.append("ModelObjectReaderWriterTest.txt");
		workspaceRule.deleteOnTearDown(location);

		ProjectDescriptionReader reader = new ProjectDescriptionReader(getWorkspace());
		// Write out the project description file
		removeFromFileSystem(location.toFile());
		try (FileOutputStream output = new FileOutputStream(location.toFile())) {
			createInputStream(projectDescription).transferTo(output);
		}
		ProjectDescription projDesc = reader.read(location);
		assertThat(projDesc).isNotNull();
	}
}
