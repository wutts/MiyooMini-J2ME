/*
 * Copyright 2022-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.woesss.j2me.micro3d;

//import static android.opengl.GLES20.*;
import org.lwjgl.*;
import org.lwjgl.opengles.*;
import static org.lwjgl.egl.EGL14.*;
import static org.lwjgl.opengles.GLES20.*;

import android.util.Log;

import java.io.IOException;
import java.nio.IntBuffer;

import javax.microedition.lcdui.Image;
//import javax.microedition.shell.AppClassLoader;

import org.recompile.mobile.Mobile;
import com.mascotcapsule.micro3d.v3.Utils;

public final class TextureImpl {
	static int sLastId;

	final TextureData image;
	private final boolean isMutable;

	int mTexId = -1;

	public TextureImpl() {
		image = new TextureData(256, 256);
		isMutable = true;
	}

	public TextureImpl(byte[] b) {
		if (b == null) {
			throw new NullPointerException();
		}
		try {
			image = Loader.loadBmpData(b, 0, b.length);
		} catch (IOException e) {
			Log.e(Utils.TAG, "Error loading data", e);
			throw new RuntimeException(e);
		}
		isMutable = false;
	}

	public TextureImpl(byte[] b, int offset, int length) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		}
		if (offset < 0 || offset + length > b.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		try {
			image = Loader.loadBmpData(b, offset, length);
		} catch (Exception e) {
			Log.e(Utils.TAG, "Error loading data", e);
			throw e;
		}
		isMutable = false;
	}

	public TextureImpl(String name) throws IOException {
		if (name == null) {
			throw new NullPointerException();
		}
		byte[] b = Mobile.getResourceAsBytes(null, name);
		if (b == null) {
			throw new IOException();
		}
		try {
			image = Loader.loadBmpData(b, 0, b.length);
		} catch (IOException e) {
			Log.e(Utils.TAG, "Error loading data from [" + name + "]", e);
			throw new RuntimeException(e);
		}
		isMutable = false;
	}

	public TextureImpl(Image image, int x, int y, int width, int height) {
		if (image == null) {
			throw new NullPointerException();
		} else if (x < 0 || y < 0 || width <= 0 || height <= 0 ||
				x + width > image.getWidth() || y + height > image.getHeight()) {
			throw new IllegalArgumentException();
		}
		isMutable = false;
		this.image = new TextureData(width, height);
		int len = width * height;
		int[] pixels = new int[len];
		image.getRGB(pixels, 0, width, x, y, width, height);
		for (int i = 0; i < len; i++) {
			int pixel = pixels[i];
			pixels[i] = pixel << 8 | pixel >>> 24;
		}
		this.image.getRaster().asIntBuffer().put(pixels);
	}

	public void dispose() {
	}

	public boolean isMutable() {
		return isMutable;
	}

	int getId() {
		if (!glIsTexture(mTexId)) {
			generateId();
		} else if (!isMutable) {
			return mTexId;
		}
		loadToGL();
		return mTexId;
	}

	int getWidth() {
		return image.width;
	}

	int getHeight() {
		return image.height;
	}

	private void generateId() {
		final IntBuffer textureIds = BufferUtils.createIntBuffer(1);
		synchronized (TextureImpl.class) {
			while (textureIds.get(0) <= sLastId) {
				textureIds.rewind();
				glGenTextures(textureIds);
			}
			sLastId = textureIds.get(0);
			mTexId = textureIds.get(0);
		}
		Render.checkGlError("glGenTextures");
	}

	private void loadToGL() {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, mTexId);

		boolean filter = Boolean.getBoolean("micro3d.v3.texture.filter");
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter ? GL_LINEAR : GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter ? GL_LINEAR : GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width, image.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image.getRaster());

		glBindTexture(GL_TEXTURE_2D, 0);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			dispose();
		} finally {
			super.finalize();
		}
	}
}
