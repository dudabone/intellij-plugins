package com.google.jstestdriver.idea.execution.generator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JstdGeneratedConfigStructure {

  private static Set<File> OUR_FILE_SYSTEM_ROOTS = Sets.newHashSet(Arrays.asList(File.listRoots()));

  private List<File> myLoadFiles = Lists.newArrayList();

  public void addLoadFile(@NotNull File loadFile) {
    try {
      File canonicalLoadFile = loadFile.getCanonicalFile();
      myLoadFiles.add(canonicalLoadFile);
    } catch (IOException e) {
      throw new RuntimeException("File.getCanonicalFile() failed for " + loadFile);
    }
  }

  @NotNull
  public String asFileContent() {
    StringBuilder builder = new StringBuilder();
    builder.append("# Generated by IDEA\n");
    Pair<String, List<String>> result = generateBasePathAndLoadPaths();
    String basePath = result.getFirst();
    if (!basePath.isEmpty()) {
      builder.append("basepath: \"").append(basePath).append("\"\n\n");
    }
    builder.append("load:\n");
    for (String path : result.getSecond()) {
      builder.append("  - \"").append(path).append("\"\n");
    }
    return builder.toString();
  }

  @Nullable
  private static File findLCA(@NotNull List<File> files) {
    List<List<File>> allParents = new ArrayList<List<File>>();
    for (File file : files) {
      File dir = file.isDirectory() ? file : file.getParentFile();
      List<File> parents = buildParents(dir);
      allParents.add(parents);
    }
    List<File> common = null;
    for (List<File> next : allParents) {
      if (common == null) {
        common = next;
      } else {
        List<File> newCommon = new ArrayList<File>();
        for (int i = 0; i < Math.min(common.size(), next.size()); i++) {
          if (common.get(i).equals(next.get(i))) {
            newCommon.add(common.get(i));
          } else {
            break;
          }
        }
        common = newCommon;
      }
    }
    if (common == null) {
      throw new RuntimeException();
    }
    if (common.isEmpty()) {
      return null;
    }
    return common.get(common.size() - 1);
  }

  @NotNull
  private Pair<String, List<String>> generateBasePathAndLoadPaths() {
    File loadFilesLCA = findLCA(myLoadFiles);
    if (loadFilesLCA == null) {
      // Windows-specific logic
      return createEmptyBasePathAndAbsoluteLoadPaths();
    }
    return createAbsoluteBasePathAndRelativeLoadPaths(loadFilesLCA);
  }

  private Pair<String, List<String>> createEmptyBasePathAndAbsoluteLoadPaths() {
    List<String> absolutePaths = new ArrayList<String>();
    for (File loadFile : myLoadFiles) {
      absolutePaths.add(loadFile.getAbsolutePath());
    }
    return Pair.create("", absolutePaths);
  }

  private Pair<String, List<String>> createAbsoluteBasePathAndRelativeLoadPaths(@NotNull File loadFilesLCA) {
    List<String> absolutePaths = new ArrayList<String>();
    for (File loadFile : myLoadFiles) {
      List<String> path = Pathfinder.FROM_ANCESTOR_EXCLUDED_TO_DESCENDANT_INCLUDED.findPath(loadFilesLCA, loadFile);
      absolutePaths.add(StringUtil.join(path, "/"));
    }
    return Pair.create(FileUtil.toSystemIndependentName(loadFilesLCA.getAbsolutePath()), absolutePaths);
  }

  private static List<File> buildParents(@NotNull final File dir) {
    List<File> parents = Lists.newArrayList();
    File f = dir;
    while (f != null) {
      parents.add(f);
      if (OUR_FILE_SYSTEM_ROOTS.contains(f)) {
        break;
      }
      f = f.getParentFile();
    }
    Collections.reverse(parents);
    return parents;
  }

  private static class Pathfinder {
    private static Pathfinder FROM_ANCESTOR_EXCLUDED_TO_DESCENDANT_INCLUDED = new Pathfinder(false, true, true);

    private final boolean myIncludeAncestor;
    private final boolean myIncludeDescendant;
    private final boolean myFromAncestorToDescendant;

    private Pathfinder(boolean includeAncestor, boolean includeDescendant, boolean fromAncestorToDescendant) {
      myIncludeAncestor = includeAncestor;
      myIncludeDescendant = includeDescendant;
      myFromAncestorToDescendant = fromAncestorToDescendant;
    }

    /**
     * Constructs string list that represent a path between ancestor directory and descendant file.
     * Whether {@code ancestor} directory and {@code descendant} are included in path is controlled by {@code myIncludeAncestor} and {@code myIncludeDescendant} flags.
     * <pre>
     *   Pathfinder pathfinder = new Pathfinder(true, false, true);
     *   List&lt;String&gt; path = pathfinder.findPath(new File("/var/www"), new File("/var/www/js-test-driver/test.js");
     *   // path is ["www", "js-test-driver"];
     * </pre>
     *
     * @param ancestor    directory, that contains descendant file
     * @param descendant  file or directory, that is contained in ancestor directory
     * @return string list of files or directories names
     */
    private List<String> findPath(@NotNull final File ancestor, @NotNull final File descendant) {
      List<String> fileNames = Lists.newArrayList();
      File file = descendant;
      boolean ancestorMet = false;
      boolean add = myIncludeDescendant;
      while (file != null) {
        if (add) {
          String name = file.getName();
          if (name.isEmpty() && OUR_FILE_SYSTEM_ROOTS.contains(file)) {
            name = file.getAbsolutePath();
          }
          fileNames.add(name);
        }
        if (ancestorMet) {
          break;
        }
        file = file.getParentFile();
        ancestorMet = ancestor.equals(file);
        add = !ancestorMet || myIncludeAncestor;
      }
      if (!ancestorMet) {
        throw new RuntimeException("Ancestor is not visited! (ancestor:'" + ancestor
            + "', descendant:'" + descendant + "')");
      }
      if (myFromAncestorToDescendant) {
        Collections.reverse(fileNames);
      }
      return fileNames;
    }

  }

}
