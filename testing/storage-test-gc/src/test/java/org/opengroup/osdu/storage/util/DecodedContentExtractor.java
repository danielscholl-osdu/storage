/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@RequiredArgsConstructor
public class DecodedContentExtractor {
    private final String inputFilenameOrContent;
    private final Predicate<String> contentAcceptanceTester;

    private boolean validOutputContentFound;
    private String outputContent;

    public String getContent() {

        validOutputContentFound = false;
        outputContent = null;

        log.info("Treat value as a content");
        if (inputFilenameOrContent.trim().isEmpty()) {
            log.info("provided value is empty. Output as is.");
            return setValidOutputContent(inputFilenameOrContent);
        }

        if (!treatValueAsAContent(inputFilenameOrContent)) {
            log.info("Value is not a valid content. Treat value as a filename");
            if (!treatValueAsAFileName(inputFilenameOrContent)){
                log.info("Value is not a filename with a valid content");
            }

        }

        return getValidOutputContentIfFound();
    }

    private boolean treatValueAsAContent(String input) {
        if (contentAcceptanceTester.test(input)) {
            log.info("the value is a valid content. Output as is.");
            setValidOutputContent(input);
            return true;
        }
        String output;
        try {
            output = new String(Base64.getDecoder().decode(input));
            log.info("the value is probably Base64 encoded. Just decoded");
            if (contentAcceptanceTester.test(output)) {
                log.info("the decoded value is a valid content. Output decoded value.");
                setValidOutputContent(output);
            } else {
                log.info("the decoded value is not a valid content.");
            }
        } catch (IllegalArgumentException e) {
            log.info("the value is not Base64 encoded. ");
        }

        return validOutputContentFound;
    }

    private boolean treatValueAsAFileName(String filename) {

        if (treatFileContent(filename)) return true;

        try {
            filename = new String(Base64.getDecoder().decode(filename));
            log.info("the filename is probably Base64 encoded. Just decoded");
            if (treatFileContent(filename)) return true;
        } catch (IllegalArgumentException e) {
            log.info("the filename is not Base64 encoded. ");
        }
        return validOutputContentFound;
    }

    private boolean treatFileContent(String filename) {
        try {
            Path path = Paths.get(filename);
            if (Files.exists(path)) {
                log.info("the filename is of existing file. Read file.");
                try {
                    String fileContent = new String(Files.readAllBytes(path));
                    if (treatValueAsAContent(fileContent)) {
                        return true;
                    }
                } catch (IOException | SecurityException | OutOfMemoryError ex) {
                    log.info(() -> ("unable to read the file: " + ex.getClass().getSimpleName()));
                }
            }
        } catch (InvalidPathException ex) {
            log.info("the filename is not valid or the file doesn't exist.");
        }
        return false;
    }

    private String setValidOutputContent(String outputContent) {
        this.outputContent = outputContent;
        this.validOutputContentFound = true;
        return getValidOutputContentIfFound();
    }

    public String getValidOutputContentIfFound() {
        return validOutputContentFound ? outputContent : null;
    }
}
