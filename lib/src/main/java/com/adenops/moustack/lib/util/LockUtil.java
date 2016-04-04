/**
 * Copyright (C) 2016 Adenops Consultants Informatique Inc.
 *
 * This file is part of the Moustack project, see http://www.moustack.org for
 * more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adenops.moustack.lib.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

public class LockUtil {
	public static void releaseLock(FileLock lock, File lockFile) {
		if (lock != null) {
			try {
				lock.release();
			} catch (IOException e) {
			}
		}

		if (lock != null && lock.channel() != null) {
			try {
				lock.channel().close();
			} catch (IOException e) {
			}
		}

		lockFile.delete();
	}

	@SuppressWarnings("resource")
	public static FileLock acquireLock(File lockFile) throws IOException {
		return new RandomAccessFile(lockFile, "rw").getChannel().tryLock();
	}
}
