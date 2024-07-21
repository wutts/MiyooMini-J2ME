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

// import static android.opengl.GLES20.*;

// import android.graphics.Bitmap;
// import android.graphics.Canvas;
// import android.graphics.PorterDuff;
// import android.graphics.Rect;
// import android.opengl.GLU;
// import android.opengl.GLUtils;
import android.util.Log;

import java.awt.image.BufferedImage;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengles.*;
import static org.lwjgl.egl.EGL14.*;
import static org.lwjgl.opengles.GLES20.*;

import com.mascotcapsule.micro3d.v3.Graphics3D;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;


import javax.microedition.lcdui.Graphics;
import org.recompile.mobile.PlatformGraphics;
import java.awt.Rectangle;  
import java.awt.Color; 

import ru.woesss.j2me.micro3d.RenderNode.FigureNode;
import com.mascotcapsule.micro3d.v3.Utils;

public class Render {
	private static final FloatBuffer BG_VBO = BufferUtils.createFloatBuffer(8 * 2)
			.put(new float[]{
					-1.0f, -1.0f, 0.0f, 0.0f,
					1.0f, -1.0f, 1.0f, 0.0f,
					-1.0f, 1.0f, 0.0f, 1.0f,
					1.0f, 1.0f, 1.0f, 1.0f
			});

	private static final int PDATA_COLOR_MASK = (Graphics3D.PDATA_COLOR_PER_COMMAND | Graphics3D.PDATA_COLOR_PER_FACE);
	private static final int PDATA_COLOR_PER_VERTEX = PDATA_COLOR_MASK;
	private static final int PDATA_NORMAL_MASK = Graphics3D.PDATA_NORMAL_PER_VERTEX;
	private static final int PDATA_TEXCOORD_MASK = Graphics3D.PDATA_TEXURE_COORD;
	private static final int[] PRIMITIVE_SIZES = {0, 1, 2, 3, 4, 1};

	final Environment env = new Environment();
	private long eglDisplay;
	private long eglWindowSurface=EGL_NO_SURFACE;
	private long eglConfig;
	private long eglContext=EGL_NO_CONTEXT;
	private final IntBuffer bgTextureId = BufferUtils.createIntBuffer(1).put(-1);
	private final float[] MVP_TMP = new float[16];

	private PlatformGraphics targetGraphics;
	private BufferedImage mBitmapBuffer;
	
	private final Rect gClip = new Rect();
	private final Rect clip = new Rect();
	private boolean backCopied;
	private final LinkedList<RenderNode> stack = new LinkedList<>();
	private int flushStep;
	private final boolean postCopy2D = !Boolean.getBoolean("micro3d.v3.render.no-mix2D3D");
	private final boolean preCopy2D = !Boolean.getBoolean("micro3d.v3.render.background.ignore");
	private IntBuffer bufHandles;
	private int clearColor;
	private TextureImpl targetTexture;

	/**
	 * Utility method for debugging OpenGL calls.
	 * <p>
	 * If the operation is not successful, the check throws an error.
	 *
	 * @param glOperation - Name of the OpenGL call to check.
	 */
	static void checkGlError(String glOperation) {
		int error = glGetError();
		if (error != GL_NO_ERROR) {
			//String s = GLU.gluErrorString(error);
			Log.e("micro3d", glOperation + ": glError " + error);
			throw new RuntimeException(glOperation + ": glError " + error);
		}
	}

	public static Render getRender() {
		return InstanceHolder.instance;
	}

	private void init() {
		
		GLES.createCapabilities();
		
		eglDisplay =  Utils.getDisplay();

		int[] versionMain = new int[1];
		int[] versionMin = new int[1];
		eglInitialize(eglDisplay, versionMain, versionMin);

		int EGL_OPENGL_ES2_BIT = 0x0004;
		int[] num_config = new int[1];
		int[] attribs = {
				EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
				EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
				EGL_RED_SIZE, 8,
				EGL_GREEN_SIZE, 8,
				EGL_BLUE_SIZE, 8,
				EGL_ALPHA_SIZE, 8,
				EGL_DEPTH_SIZE, 16,
				EGL_STENCIL_SIZE, EGL_DONT_CARE,
				EGL_NONE
		};
		PointerBuffer eglConfigs = org.lwjgl.BufferUtils.createPointerBuffer(1);
		eglChooseConfig(eglDisplay, attribs, eglConfigs,  num_config);
		eglConfig = eglConfigs.get(0);

		int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
		int[] attrib_list = {
				EGL_CONTEXT_CLIENT_VERSION, 2,
				EGL_NONE
		};
		eglContext = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, attrib_list);
	}

	public synchronized void bind(Graphics graphics) {
		this.targetGraphics = (PlatformGraphics)graphics;
		// Canvas canvas = graphics.getCanvas();
		// int width = canvas.getWidth();
		// int height = canvas.getHeight();
		mBitmapBuffer = this.targetGraphics.getCanvas();
		int width = mBitmapBuffer.getWidth();
		int height = mBitmapBuffer.getHeight();
		
		if (eglContext == EGL_NO_CONTEXT) {
			init();
		}
		
		if (env.width != width || env.height != height) {

			if (eglWindowSurface != EGL_NO_SURFACE) {
				releaseEglContext();
				eglDestroySurface(eglDisplay, eglWindowSurface);
			}

			int[] surface_attribs = {
					EGL_WIDTH, width,
					EGL_HEIGHT, height,
					EGL_NONE};
			eglWindowSurface = eglCreatePbufferSurface(eglDisplay, eglConfig, surface_attribs);
			eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext);

			glViewport(0, 0, width, height);
			Program.create();
			env.width = width;
			env.height = height;
			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);
		}
		eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext);
		Rect clip = this.clip;
		//canvas.getClipBounds(clip);
		Rectangle clipBounds = this.targetGraphics.getGraphics2D().getClipBounds();  
        if (clipBounds != null) {  
            clip.set(clipBounds.x,clipBounds.y,clipBounds.x+clipBounds.width,clipBounds.y+clipBounds.height);
			//System.out.println("clip: left " +clip.left+" top "+clip.top+" right "+clip.right+" bottom "+clip.bottom);
        }
		
		
		int l = clip.left;
		int t = clip.top;
		int r = clip.right;
		int b = clip.bottom;
		gClip.set(l, t, r, b);
		if (l == 0 && t == 0 && r == env.width && b == env.height) {
			glDisable(GL_SCISSOR_TEST);
		} else {
			GLES.createCapabilities();
			
			glEnable(GL_SCISSOR_TEST);
			glScissor(l, t, r - l, b - t);
		}
		glClear(GL_DEPTH_BUFFER_BIT);
		backCopied = false;
		eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
	}

	public synchronized void bind(TextureImpl tex) {
		targetTexture = tex;
		int width = tex.getWidth();
		int height = tex.getHeight();
		if (eglContext == EGL_NO_CONTEXT) {
			init();
		}
		
		if (env.width != width || env.height != height) {

			if (eglWindowSurface != EGL_NO_SURFACE) {
				releaseEglContext();
				eglDestroySurface(eglDisplay, eglWindowSurface);
			}

			int[] surface_attribs = {
					EGL_WIDTH, width,
					EGL_HEIGHT, height,
					EGL_NONE};
			eglWindowSurface = eglCreatePbufferSurface(eglDisplay, eglConfig, surface_attribs);
			eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext);

			glViewport(0, 0, width, height);
			Program.create();
			env.width = width;
			env.height = height;
		}
		eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext);
		Rect clip = this.clip;
		clip.set(0, 0, width, height);
		int l = clip.left;
		int t = clip.top;
		int r = clip.right;
		int b = clip.bottom;
		gClip.set(l, t, r, b);
		if (l == 0 && t == 0 && r == env.width && b == env.height) {
			glDisable(GL_SCISSOR_TEST);
		} else {
			glEnable(GL_SCISSOR_TEST);
			glScissor(l, t, r - l, b - t);
		}
		glClearColor(
				((clearColor >> 16) & 0xff) / 255.0f,
				((clearColor >> 8) & 0xff) / 255.0f,
				(clearColor & 0xff) / 255.0f,
				1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		backCopied = false;
		eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

	}

	private static void applyBlending(int blendMode) {
		switch (blendMode) {
			case Model.Polygon.BLEND_HALF:
				glEnable(GL_BLEND);
				glBlendColor(0.5f, 0.5f, 0.5f, 1.0f);
				glBlendEquation(GL_FUNC_ADD);
				glBlendFunc(GL_CONSTANT_COLOR, GL_CONSTANT_COLOR);
				break;
			case Model.Polygon.BLEND_ADD:
				glEnable(GL_BLEND);
				glBlendEquation(GL_FUNC_ADD);
				glBlendFunc(GL_ONE, GL_ONE);
				break;
			case Model.Polygon.BLEND_SUB:
				glEnable(GL_BLEND);
				glBlendEquation(GL_FUNC_REVERSE_SUBTRACT);
				glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
				break;
			default:
				glDisable(GL_BLEND);
		}
	}

	private void copy2d(boolean preProcess) {
		if (targetTexture != null) {// render to texture
			return;
		}
		
		GLES.createCapabilities();
		if (!glIsTexture(bgTextureId.get(0))) {
			bgTextureId.rewind();
			glGenTextures(bgTextureId);
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, bgTextureId.get(0));
			boolean filter = Boolean.getBoolean("micro3d.v3.background.filter");
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		} else {
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, bgTextureId.get(0));
		}
		
		int width = mBitmapBuffer.getWidth();
		int height = mBitmapBuffer.getHeight();
		
		int[] pixels=mBitmapBuffer.getRGB(0, 0, width, height, null, 0, width);
		int[] dst=new int[width*height];
		//IntBuffer bytedata = BufferUtils.createIntBuffer(width*height);
		colorConvert(pixels, dst);
		//bytedata.put(dst);
		//bytedata.rewind();
		
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, dst);
		
		//GLUtils.texImage2D(GL_TEXTURE_2D, 0, targetImage, 0);
		checkGlError("texImage2D");

		final Program.Simple program = Program.simple;
		program.use();

		BG_VBO.rewind();
		glVertexAttribPointer(program.aPosition, 2, GL_FLOAT, false, 4 * 4, BG_VBO);
		glEnableVertexAttribArray(program.aPosition);

		// координаты текстур
		BG_VBO.position(2);
		glVertexAttribPointer(program.aTexture, 2, GL_FLOAT, false, 4 * 4, BG_VBO);
		glEnableVertexAttribArray(program.aTexture);

		if (preProcess) {
			glDisable(GL_BLEND);
		} else {
			glEnable(GL_BLEND);
			glBlendEquation(GL_FUNC_ADD);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		}
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
		glDisableVertexAttribArray(program.aPosition);
		glDisableVertexAttribArray(program.aTexture);
		checkGlError("copy2d");
		if (!preProcess) {
			return;
		}
		if (postCopy2D) {
			 // 实际上不会改变画布上的任何内容，因为它试图用一个完全透明的颜色来填充画布，把画布置0
			// targetImage.setHasAlpha(true);
			// Canvas canvas = new Canvas(targetImage);
			// canvas.clipRect(gClip);
			// canvas.drawColor(0, PorterDuff.Mode.SRC);
			// targetImage.setHasAlpha(false);
			targetGraphics.getGraphics2D().setBackground(new Color(0,0,0,0));
			targetGraphics.getGraphics2D().clearRect(gClip.left, gClip.top, gClip.width(), gClip.height());
			// for(int i=0;i<width*height;i++)
			// {
				// dst[i]=0;
			// }
			//mBitmapBuffer.setRGB(0, 0, width, height, dst, 0, width);
		}
		backCopied = true;
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			// Destroy EGL
			
			eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
			if (eglWindowSurface != EGL_NO_SURFACE)
				eglDestroySurface(eglDisplay, eglWindowSurface);
			eglDestroyContext(eglDisplay, eglContext);
			eglTerminate(eglDisplay);
		} finally {
			super.finalize();
		}
	}

	void renderFigure(Model model,
					  TextureImpl[] textures,
					  int attrs,
					  float[] projMatrix,
					  float[] viewMatrix,
					  FloatBuffer vertices,
					  FloatBuffer normals,
					  Light light,
					  TextureImpl specular,
					  int toonThreshold,
					  int toonHigh,
					  int toonLow) {
		boolean isTransparency = (attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0;
		if (!isTransparency && flushStep == 2) {
			return;
		} else if (!model.hasPolyT && !model.hasPolyC) {
			return;
		}

		glEnable(GL_DEPTH_TEST);
		glDepthMask(flushStep == 1);
		MathUtil.multiplyMM(MVP_TMP, projMatrix, viewMatrix);
		if (bufHandles == null) {
			bufHandles = BufferUtils.createIntBuffer(3);
			glGenBuffers(bufHandles);
		}
		try {
			glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(0));
			glBufferData(GL_ARRAY_BUFFER, vertices.rewind(), GL_STREAM_DRAW);

			ByteBuffer texCoords = model.texCoordArray;
			glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(1));
			glBufferData(GL_ARRAY_BUFFER, texCoords.rewind(), GL_STREAM_DRAW);

			boolean isLight = (attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0 && normals != null;
			if (isLight) {
				glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(2));
				glBufferData(GL_ARRAY_BUFFER, normals.rewind(), GL_STREAM_DRAW);
			}
			if (model.hasPolyT) {
				final Program.Tex program = Program.tex;
				program.use();

				glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(0));
				glEnableVertexAttribArray(program.aPosition);
				glVertexAttribPointer(program.aPosition, 3, GL_FLOAT, false, 3 * 4, 0);

				glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(1));
				glEnableVertexAttribArray(program.aColorData);
				glVertexAttribPointer(program.aColorData, 2, GL_UNSIGNED_BYTE, false, 5, 0);
				glEnableVertexAttribArray(program.aMaterial);
				glVertexAttribPointer(program.aMaterial, 3, GL_UNSIGNED_BYTE, false, 5, 2);

				if (isLight) {
					glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(2));
					glEnableVertexAttribArray(program.aNormal);
					glVertexAttribPointer(program.aNormal, 3, GL_FLOAT, false, 3 * 4, 0);
					program.setToonShading(attrs, toonThreshold, toonHigh, toonLow);
					program.setLight(light);
					program.setSphere((attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) == 0 ? null : specular);
				} else {
					glDisableVertexAttribArray(program.aNormal);
					program.setLight(null);
				}

				program.bindMatrices(MVP_TMP, viewMatrix);
				// Draw triangles
				renderModel(textures, model, isTransparency);
				glDisableVertexAttribArray(program.aPosition);
				glDisableVertexAttribArray(program.aColorData);
				glDisableVertexAttribArray(program.aMaterial);
				glDisableVertexAttribArray(program.aNormal);
				glBindBuffer(GL_ARRAY_BUFFER, 0);
			}

			if (model.hasPolyC) {
				final Program.Color program = Program.color;
				program.use();

				int offset = model.numVerticesPolyT;
				glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(0));
				glEnableVertexAttribArray(program.aPosition);
				glVertexAttribPointer(program.aPosition, 3, GL_FLOAT, false, 3 * 4, 3 * 4 * offset);

				glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(1));
				glVertexAttribPointer(program.aColorData, 3, GL_UNSIGNED_BYTE, true, 5, 5 * offset);
				glEnableVertexAttribArray(program.aColorData);
				glEnableVertexAttribArray(program.aMaterial);
				glVertexAttribPointer(program.aMaterial, 2, GL_UNSIGNED_BYTE, false, 5, 5 * offset + 3);

				if (isLight) {
					glBindBuffer(GL_ARRAY_BUFFER, bufHandles.get(2));
					glVertexAttribPointer(program.aNormal, 3, GL_FLOAT, false, 3 * 4, 3 * 4 * offset);
					glEnableVertexAttribArray(program.aNormal);
					program.setLight(light);
					program.setSphere((attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) == 0 ? null : specular);
					program.setToonShading(attrs, toonThreshold, toonHigh, toonLow);
				} else {
					glDisableVertexAttribArray(program.aNormal);
					program.setLight(null);
				}
				program.bindMatrices(MVP_TMP, viewMatrix);
				renderModel(model, isTransparency);
				glDisableVertexAttribArray(program.aPosition);
				glDisableVertexAttribArray(program.aColorData);
				glDisableVertexAttribArray(program.aMaterial);
				glDisableVertexAttribArray(program.aNormal);
			}
		} finally {
			glBindBuffer(GL_ARRAY_BUFFER, 0);
		}
	}

	private void renderModel(TextureImpl[] textures, Model model, boolean enableBlending) {
		if (textures == null || textures.length == 0) return;
		Program.Tex program = Program.tex;
		int[][][] meshes = model.subMeshesLengthsT;
		int length = meshes.length;
		int blendMode = 0;
		int pos = 0;
		if (flushStep == 1) {
			if (enableBlending) length = 1;
			glDisable(GL_BLEND);
		} else {
			int[][] mesh = meshes[blendMode++];
			int cnt = 0;
			for (int[] lens : mesh) {
				for (int len : lens) {
					cnt += len;
				}
			}
			pos += cnt;
		}
		while (blendMode < length) {
			int[][] texMesh = meshes[blendMode];
			if (flushStep == 2) {
				applyBlending(blendMode << 1);
			}
			for (int face = 0; face < texMesh.length; face++) {
				int[] lens = texMesh[face];
				if (face >= textures.length) {
					program.setTex(null);
				} else {
					TextureImpl tex = textures[face];
					program.setTex(tex);
				}
				int cnt = lens[0];
				if (cnt > 0) {
					glEnable(GL_CULL_FACE);
					glDrawArrays(GL_TRIANGLES, pos, cnt);
					pos += cnt;
				}
				cnt = lens[1];
				if (cnt > 0) {
					glDisable(GL_CULL_FACE);
					glDrawArrays(GL_TRIANGLES, pos, cnt);
					pos += cnt;
				}
			}
			blendMode++;
		}
		checkGlError("glDrawArrays");
	}

	private void renderModel(Model model, boolean enableBlending) {
		int[][] meshes = model.subMeshesLengthsC;
		int length = meshes.length;
		int pos = 0;
		int blendMode = 0;
		if (flushStep == 1) {
			if (enableBlending) length = 1;
			glDisable(GL_BLEND);
		} else {
			int[] mesh = meshes[blendMode++];
			int cnt = 0;
			for (int len : mesh) {
				cnt += len;
			}
			pos += cnt;
		}
		while (blendMode < length) {
			int[] mesh = meshes[blendMode];
			if (flushStep == 2) {
				applyBlending(blendMode << 1);
			}
			int cnt = mesh[0];
			if (cnt > 0) {
				glEnable(GL_CULL_FACE);
				glDrawArrays(GL_TRIANGLES, pos, cnt);
				pos += cnt;
			}
			cnt = mesh[1];
			if (cnt > 0) {
				glDisable(GL_CULL_FACE);
				glDrawArrays(GL_TRIANGLES, pos, cnt);
				pos += cnt;
			}
			blendMode++;
		}
		checkGlError("glDrawArrays");
	}

	public synchronized void release() {
		stack.clear();
		bindEglContext();
		if (targetTexture != null) {
			glReadPixels(0, 0, 256, 256, GL_RGBA, GL_UNSIGNED_BYTE, targetTexture.image.getRaster());
			targetTexture = null;
		} else if (targetGraphics != null) {
			if (postCopy2D) {//这里为true才能显示鬼泣文字,这里为false珍宝塔可以正常显示
				copy2d(false);
			}
			Rect clip = this.gClip;
			
			int width = mBitmapBuffer.getWidth();
			int height = mBitmapBuffer.getHeight();
			int[] data=new int[width*height];
			Utils.glReadPixels(clip.left, clip.top, clip.width(), clip.height(), data, width, width*4);
			
			for(int i=0;i<width*height;i++)
			{
				data[i]=rgbaToArgb(data[i]);
				//data[i] |= 0xFF000000;
			}
			mBitmapBuffer.setRGB(0, 0, width, height, data, 0, width);	
			
			targetGraphics = null;
		}
		releaseEglContext();
	}

	public synchronized void flush() {
		if (stack.isEmpty()) {
			return;
		}
		bindEglContext();
		try {
			if (!backCopied && preCopy2D) {
				copy2d(true);
			}
			flushStep = 1;
			for (RenderNode r : stack) {
				r.render(this);
			}
			flushStep = 2;
			for (RenderNode r : stack) {
				r.render(this);
				r.recycle();
			}
			glDisable(GL_BLEND);
			glDepthMask(true);
			glClear(GL_DEPTH_BUFFER_BIT);
			glFlush();
		} finally {
			stack.clear();
			releaseEglContext();
		}
	}

	private void renderMeshC(RenderNode.PrimitiveNode node) {
		int command = node.command;
		Program.Color program = Program.color;
		program.use();
		if ((node.attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0 && (command & Graphics3D.PATTR_LIGHTING) != 0 && node.normals != null) {
			TextureImpl sphere = node.specular;
			if ((node.attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) != 0 && (command & Graphics3D.PATTR_SPHERE_MAP) != 0 && sphere != null) {
				glVertexAttrib2f(program.aMaterial, 1, 1);
				program.setSphere(sphere);
			} else {
				glVertexAttrib2f(program.aMaterial, 1, 0);
				program.setSphere(null);
			}
			program.setLight(node.light);
			program.setToonShading(node.attrs, node.toonThreshold, node.toonHigh, node.toonLow);

			glVertexAttribPointer(program.aNormal, 3, GL_FLOAT, false, 3 * 4, node.normals.rewind());
			glEnableVertexAttribArray(program.aNormal);
		} else {
			glVertexAttrib2f(program.aMaterial, 0, 0);
			program.setLight(null);
			glDisableVertexAttribArray(program.aNormal);
		}

		program.bindMatrices(MVP_TMP, node.viewMatrix);

		glVertexAttribPointer(program.aPosition, 3, GL_FLOAT, false, 3 * 4, node.vertices.rewind());
		glEnableVertexAttribArray(program.aPosition);

		if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
			program.setColor(node.colors);
		} else {
			glVertexAttribPointer(program.aColorData, 3, GL_UNSIGNED_BYTE, true, 3, node.colors.rewind());
			glEnableVertexAttribArray(program.aColorData);
		}

		glDrawArrays(GL_TRIANGLES, 0, node.vertices.capacity() / 3);
		glDisableVertexAttribArray(program.aPosition);
		glDisableVertexAttribArray(program.aColorData);
		glDisableVertexAttribArray(program.aNormal);
		checkGlError("renderMeshC");
	}

	private void renderMeshT(RenderNode.PrimitiveNode node) {
		int command = node.command;
		Program.Tex program = Program.tex;
		program.use();
		if ((node.attrs & Graphics3D.ENV_ATTR_LIGHTING) != 0 && (command & Graphics3D.PATTR_LIGHTING) != 0 && node.normals != null) {
			TextureImpl sphere = node.specular;
			if ((node.attrs & Graphics3D.ENV_ATTR_SPHERE_MAP) != 0 && (command & Graphics3D.PATTR_SPHERE_MAP) != 0 && sphere != null) {
				glVertexAttrib3f(program.aMaterial, 1, 1, command & Graphics3D.PATTR_COLORKEY);
				program.setSphere(sphere);
			} else {
				glVertexAttrib3f(program.aMaterial, 1, 0, command & Graphics3D.PATTR_COLORKEY);
				program.setSphere(null);
			}
			program.setLight(node.light);
			program.setToonShading(node.attrs, node.toonThreshold, node.toonHigh, node.toonLow);

			glVertexAttribPointer(program.aNormal, 3, GL_FLOAT, false, 3 * 4, node.normals.rewind());
			glEnableVertexAttribArray(program.aNormal);
		} else {
			glVertexAttrib3f(program.aMaterial, 0, 0, command & Graphics3D.PATTR_COLORKEY);
			program.setLight(null);
			glDisableVertexAttribArray(program.aNormal);
		}

		program.bindMatrices(MVP_TMP, node.viewMatrix);

		glVertexAttribPointer(program.aPosition, 3, GL_FLOAT, false, 3 * 4, node.vertices.rewind());
		glEnableVertexAttribArray(program.aPosition);

		glVertexAttribPointer(program.aColorData, 2, GL_UNSIGNED_BYTE, false, 2, node.texCoords.rewind());
		glEnableVertexAttribArray(program.aColorData);

		program.setTex(node.texture);

		glDrawArrays(GL_TRIANGLES, 0, node.vertices.capacity() / 3);

		glDisableVertexAttribArray(program.aPosition);
		glDisableVertexAttribArray(program.aColorData);
		glDisableVertexAttribArray(program.aNormal);
		checkGlError("renderMeshT");
	}

	public void drawCommandList(int[] cmds) {
		if (Graphics3D.COMMAND_LIST_VERSION_1_0 != cmds[0]) {
			throw new IllegalArgumentException("Unsupported command list version: " + cmds[0]);
		}
		for (int i = 1; i < cmds.length; ) {
			int cmd = cmds[i++];
			switch (cmd & 0xFF000000) {
				case Graphics3D.COMMAND_AFFINE_INDEX:
					selectAffineTrans(cmd & 0xFFFFFF);
					break;
				case Graphics3D.COMMAND_AMBIENT_LIGHT: {
					env.light.ambIntensity = i++;
					break;
				}
				case Graphics3D.COMMAND_ATTRIBUTE:
					env.attrs = cmd & 0xFFFFFF;
					break;
				case Graphics3D.COMMAND_CENTER:
					setCenter(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_CLIP:
					clip.intersect(cmds[i++], cmds[i++], cmds[i++], cmds[i++]);
					updateClip();
					break;
				case Graphics3D.COMMAND_DIRECTION_LIGHT: {
					env.light.x = i++;
					env.light.y = i++;
					env.light.z = i++;
					env.light.dirIntensity = i++;
					break;
				}
				case Graphics3D.COMMAND_FLUSH:
					flush();
					break;
				case Graphics3D.COMMAND_NOP:
					i += cmd & 0xFFFFFF;
					break;
				case Graphics3D.COMMAND_PARALLEL_SCALE:
					setOrthographicScale(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PARALLEL_SIZE:
					setOrthographicWH(cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PERSPECTIVE_FOV:
					setPerspectiveFov(cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_PERSPECTIVE_WH:
					setPerspectiveWH(cmds[i++], cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_TEXTURE_INDEX:
					int tid = cmd & 0xFFFFFF;
					if (tid > 0 && tid < 16) {
						env.textureIdx = tid;
					}
					break;
				case Graphics3D.COMMAND_THRESHOLD:
					setToonParam(cmds[i++], cmds[i++], cmds[i++]);
					break;
				case Graphics3D.COMMAND_END:
					return;
				default:
					int type = cmd & 0x7000000;
					if (type == 0 || cmd < 0) {
						throw new IllegalArgumentException();
					}
					int num = cmd >> 16 & 0xFF;
					int sizeOf = PRIMITIVE_SIZES[type >> 24];
					int len = num * 3 * sizeOf;
					int vo = i;
					i += len;
					int no = i;
					if ((cmd & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
						i += num * 3;
					} else if ((cmd & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
						i += len;
					}
					int to = i;
					if (type == Graphics3D.PRIMITVE_POINT_SPRITES) {
						if ((cmd & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD) {
							i += 8;
						} else if ((cmd & PDATA_TEXCOORD_MASK) != Graphics3D.PDATA_TEXURE_COORD_NONE) {
							i += num * 8;
						}
					} else if ((cmd & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD) {
						i += num * 2 * sizeOf;
					}

					int co = i;
					if ((cmd & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
						i++;
					} else if ((cmd & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_FACE) {
						i += num;
					} else if ((cmd & PDATA_COLOR_MASK) == PDATA_COLOR_PER_VERTEX) {
						i += num * sizeOf;
					}
					if (i > cmds.length) {
						throw new IllegalArgumentException();
					}
					postPrimitives(cmd, cmds, vo, cmds, no, cmds, to, cmds, co);
					break;
			}
		}
	}

	private void updateClip() {
		bindEglContext();
		Rect clip = this.clip;
		int l = clip.left;
		int t = clip.top;
		int r = clip.right;
		int b = clip.bottom;
		if (l == 0 && t == 0 && r == env.width && b == env.height) {
			glDisable(GL_SCISSOR_TEST);
		} else {
			glEnable(GL_SCISSOR_TEST);
			glScissor(l, t, r - l, b - t);
		}
		releaseEglContext();
	}

	public synchronized void postFigure(FigureImpl figure) {
		FigureNode rn;
		if (figure.stack.empty()) {
			rn = new FigureNode(this, figure);
		} else {
			rn = figure.stack.pop();
			rn.setData(this);
		}
		stack.add(rn);
	}

	public synchronized void postPrimitives(int command,
											int[] vertices, int vo,
											int[] normals, int no,
											int[] textureCoords, int to,
											int[] colors, int co) {
		if (command < 0) {
			throw new IllegalArgumentException();
		}
		int numPrimitives = command >> 16 & 0xff;
		FloatBuffer vcBuf;
		FloatBuffer ncBuf = null;
		ByteBuffer tcBuf = null;
		ByteBuffer colorBuf = null;
		switch ((command & 0x7000000)) {
			case Graphics3D.PRIMITVE_POINTS: {
				int vcLen = numPrimitives * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}

				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						colorBuf.put((byte) (color >> 16 & 0xFF));
						colorBuf.put((byte) (color >> 8 & 0xFF));
						colorBuf.put((byte) (color & 0xFF));
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_LINES: {
				int vcLen = numPrimitives * 2 * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}

				if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_TRIANGLES: {
				int vcLen = numPrimitives * 3 * 3;
				vcBuf = BufferUtils.createFloatBuffer(vcLen);
				for (int i = 0; i < vcLen; i++) {
					vcBuf.put(vertices[vo++]);
				}
				if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
					ncBuf = BufferUtils.createFloatBuffer(vcLen);
					for (int end = no + numPrimitives * 3; no < end; ) {
						float x = normals[no++];
						float y = normals[no++];
						float z = normals[no++];
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
					}
				} else if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
					ncBuf = BufferUtils.createFloatBuffer(vcLen);
					for (int end = no + vcLen; no < end; ) {
						ncBuf.put(normals[no++]);
					}
				}
				if ((command & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD) {
					if (env.getTexture() == null) {
						return;
					}
					int tcLen = numPrimitives * 3 * 2;
					tcBuf = BufferUtils.createByteBuffer(tcLen);
					for (int i = 0; i < tcLen; i++) {
						tcBuf.put((byte) textureCoords[to++]);
					}
				} else if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(vcLen);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_QUADS: {
				vcBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
				for (int i = 0; i < numPrimitives; i++) {
					int offset = vo + i * 4 * 3;
					int pos = offset;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // A
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // B
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos++]); // C
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // D
					pos = offset;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // A
					pos = offset + 2 * 3;
					vcBuf.put(vertices[pos++]).put(vertices[pos++]).put(vertices[pos]);   // C
				}
				if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_FACE) {
					ncBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
					for (int end = no + numPrimitives * 3; no < end; ) {
						float x = normals[no++];
						float y = normals[no++];
						float z = normals[no++];
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
						ncBuf.put(x).put(y).put(z);
					}
				} else if ((command & PDATA_NORMAL_MASK) == Graphics3D.PDATA_NORMAL_PER_VERTEX) {
					ncBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 3);
					for (int i = 0; i < numPrimitives; i++) {
						int offset = no + i * 4 * 3;
						int pos = offset;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // A
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // B
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos++]); // C
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // D
						pos = offset;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // A
						pos = offset + 2 * 3;
						ncBuf.put(normals[pos++]).put(normals[pos++]).put(normals[pos]);   // C
					}
				}
				if ((command & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD) {
					if (env.getTexture() == null) {
						return;
					}
					tcBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 2);
					for (int i = 0; i < numPrimitives; i++) {
						int offset = to + i * 4 * 2;
						int pos = offset;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // A
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // B
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos++]); // C
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // D
						pos = offset;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // A
						pos = offset + 2 * 2;
						tcBuf.put((byte) textureCoords[pos++]).put((byte) textureCoords[pos]);   // C
					}
				} else if ((command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
					colorBuf = BufferUtils.createByteBuffer(3);
					int color = colors[co];
					colorBuf.put((byte) (color >> 16 & 0xFF));
					colorBuf.put((byte) (color >> 8 & 0xFF));
					colorBuf.put((byte) (color & 0xFF));
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					colorBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 3);
					for (int i = 0; i < numPrimitives; i++) {
						int color = colors[co++];
						byte r = (byte) (color >> 16 & 0xFF);
						byte g = (byte) (color >> 8 & 0xFF);
						byte b = (byte) (color & 0xFF);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
						colorBuf.put(r).put(g).put(b);
					}
				} else {
					return;
				}
				break;
			}
			case Graphics3D.PRIMITVE_POINT_SPRITES: {
				if (env.getTexture() == null) {
					return;
				}
				int psParams = command & PDATA_TEXCOORD_MASK;
				if (psParams == 0) {
					return;
				}

				float[] vertex = new float[6 * 4];

				vcBuf = BufferUtils.createFloatBuffer(numPrimitives * 6 * 4);
				tcBuf = BufferUtils.createByteBuffer(numPrimitives * 6 * 2);
				int angle = 0;
				float halfWidth = 0;
				float halfHeight = 0;
				byte tx0 = 0;
				byte ty0 = 0;
				byte tx1 = 0;
				byte ty1 = 0;
				MathUtil.multiplyMM(MVP_TMP, env.projMatrix, env.viewMatrix);
				for (int i = 0; i < numPrimitives; i++) {
					vertex[4] = vertices[vo++];
					vertex[5] = vertices[vo++];
					vertex[6] = vertices[vo++];
					vertex[7] = 1.0f;
					Utils.multiplyMV(vertex, MVP_TMP);

					if (psParams != Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD || i == 0) {
						float width = textureCoords[to++];
						float height = textureCoords[to++];
						angle = textureCoords[to++];
						tx0 = (byte) textureCoords[to++];
						ty0 = (byte) textureCoords[to++];
						tx1 = (byte) (textureCoords[to++] - 1);
						ty1 = (byte) (textureCoords[to++] - 1);
						switch (textureCoords[to++]) {
							case Graphics3D.POINT_SPRITE_LOCAL_SIZE | Graphics3D.POINT_SPRITE_PERSPECTIVE:
								halfWidth = width * env.projMatrix[0] * 0.5f;
								halfHeight = height * env.projMatrix[5] * 0.5f;
								break;
							case Graphics3D.POINT_SPRITE_PIXEL_SIZE | Graphics3D.POINT_SPRITE_PERSPECTIVE:
								if (env.projection <= Graphics3D.COMMAND_PARALLEL_SIZE) {
									halfWidth = width / env.width;
									halfHeight = height / env.height;
								} else {
									halfWidth = width / env.width * env.near;
									halfHeight = height / env.height * env.near;
								}
								break;
							case Graphics3D.POINT_SPRITE_LOCAL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS:
								if (env.projection <= Graphics3D.COMMAND_PARALLEL_SIZE) {
									halfWidth = width * env.projMatrix[0] * 0.5f;
									halfHeight = height * env.projMatrix[5] * 0.5f;
								} else {
									float near = env.near;
									halfWidth = width * env.projMatrix[0] / near * 0.5f * vertex[3];
									halfHeight = height * env.projMatrix[5] / near * 0.5f * vertex[3];
								}
								break;
							case Graphics3D.POINT_SPRITE_PIXEL_SIZE | Graphics3D.POINT_SPRITE_NO_PERS:
								halfWidth = width / env.width * vertex[3];
								halfHeight = height / env.height * vertex[3];
								break;
							default:
								throw new IllegalArgumentException();
						}
					}

					Utils.getSpriteVertex(vertex, angle, halfWidth, halfHeight);
					vcBuf.put(vertex);

					tcBuf.put(tx0).put(ty1);
					tcBuf.put(tx0).put(ty0);
					tcBuf.put(tx1).put(ty1);
					tcBuf.put(tx1).put(ty1);
					tcBuf.put(tx0).put(ty0);
					tcBuf.put(tx1).put(ty0);
				}
				break;
			}
			default:
				throw new IllegalArgumentException();
		}
		stack.add(new RenderNode.PrimitiveNode(this, command, vcBuf, ncBuf, tcBuf, colorBuf));
	}

	public synchronized void drawFigure(FigureImpl figure) {
		bindEglContext();
		if (!backCopied && preCopy2D) {
			copy2d(true);
		}
		try {
			Model model = figure.model;
			figure.prepareBuffers();

			flushStep = 1;
			for (RenderNode r : stack) {
				r.render(this);
			}
			renderFigure(model,
					env.textures,
					env.attrs,
					env.projMatrix,
					env.viewMatrix,
					model.vertexArray,
					model.normalsArray,
					env.light,
					env.specular,
					env.toonThreshold,
					env.toonHigh,
					env.toonLow);

			flushStep = 2;
			for (RenderNode r : stack) {
				r.render(this);
				r.recycle();
			}
			renderFigure(model,
					env.textures,
					env.attrs,
					env.projMatrix,
					env.viewMatrix,
					model.vertexArray,
					model.normalsArray,
					env.light,
					env.specular,
					env.toonThreshold,
					env.toonHigh,
					env.toonLow);

			glDisable(GL_BLEND);
			glDepthMask(true);
			glClear(GL_DEPTH_BUFFER_BIT);
		} finally {
			releaseEglContext();
		}
	}

	private void bindEglContext() {
		eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext);
	}

	private void releaseEglContext() {
		eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
	}

	public void reset() {
		stack.clear();
	}

	public void setTexture(TextureImpl tex) {
		if (tex == null) {
			return;
		}
		env.textures[0] = tex;
		env.textureIdx = 0;
		env.texturesLen = 1;
	}

	public void setTextureArray(TextureImpl[] tex) {
		if (tex == null) {
			return;
		}
		int len = tex.length;
		System.arraycopy(tex, 0, env.textures, 0, len);
		env.texturesLen = len;
	}

	public float[] getViewMatrix() {
		return env.viewMatrix;
	}

	public void setLight(int ambIntensity, int dirIntensity, int x, int y, int z) {
		env.light.set(ambIntensity, dirIntensity, x, y, z);
	}

	public int getAttributes() {
		return env.attrs;
	}

	public void setToonParam(int tress, int high, int low) {
		env.toonThreshold = tress;
		env.toonHigh = high;
		env.toonLow = low;
	}

	public void setSphereTexture(TextureImpl tex) {
		if (tex != null) {
			env.specular = tex;
		}
	}

	public void setAttribute(int attrs) {
		env.attrs = attrs;
	}

	void renderPrimitive(RenderNode.PrimitiveNode node) {
		int command = node.command;
		int blend = (node.attrs & Graphics3D.ENV_ATTR_SEMI_TRANSPARENT) != 0 ? (command & Graphics3D.PATTR_BLEND_SUB) >> 4 : 0;
		if (blend != 0) {
			if (flushStep == 1) {
				return;
			}
		} else if (flushStep == 2) {
			return;
		}
		MathUtil.multiplyMM(MVP_TMP, node.projMatrix, node.viewMatrix);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LESS);
		glDepthMask(flushStep == 1);
		glDisable(GL_CULL_FACE);
		applyBlending(blend);
		int numPrimitives = command >> 16 & 0xff;
		switch ((command & 0x7000000)) {
			case Graphics3D.PRIMITVE_POINTS: {
				renderMesh(node, GL_POINTS);
				checkGlError("renderPrimitive[PRIMITIVE_POINTS]");
				break;
			}
			case Graphics3D.PRIMITVE_LINES: {
				renderMesh(node, GL_LINES);
				checkGlError("renderPrimitive[PRIMITIVE_LINES]");
				break;
			}
			case Graphics3D.PRIMITVE_TRIANGLES:
			case Graphics3D.PRIMITVE_QUADS: {
				if ((command & PDATA_TEXCOORD_MASK) == Graphics3D.PDATA_TEXURE_COORD) {
					renderMeshT(node);
				} else if ((command & PDATA_COLOR_MASK) != Graphics3D.PDATA_COLOR_NONE) {
					renderMeshC(node);
				}
				break;
			}
			case Graphics3D.PRIMITVE_POINT_SPRITES: {
				Program.Sprite program = Program.sprite;
				program.use();

				glVertexAttribPointer(program.aPosition, 4, GL_FLOAT, false, 4 * 4, node.vertices.rewind());
				glEnableVertexAttribArray(program.aPosition);

				glVertexAttribPointer(program.aColorData, 2, GL_UNSIGNED_BYTE, false, 2, node.texCoords.rewind());
				glEnableVertexAttribArray(program.aColorData);

				program.setTexture(node.texture);

				glUniform1i(program.uIsTransparency, (command & Graphics3D.PATTR_COLORKEY));
				glDrawArrays(GL_TRIANGLES, 0, numPrimitives * 6);
				glDisableVertexAttribArray(program.aPosition);
				glDisableVertexAttribArray(program.aColorData);
				checkGlError("renderPrimitive[PRIMITIVE_POINT_SPRITES]");
			}
		}

	}

	private void renderMesh(RenderNode.PrimitiveNode node, int type) {
		Program.Color program = Program.color;
		program.use();
		glVertexAttrib2f(program.aMaterial, 0, 0);
		program.bindMatrices(MVP_TMP, node.viewMatrix);

		glVertexAttribPointer(program.aPosition, 3, GL_FLOAT, false, 3 * 4, node.vertices.rewind());
		glEnableVertexAttribArray(program.aPosition);

		if ((node.command & PDATA_COLOR_MASK) == Graphics3D.PDATA_COLOR_PER_COMMAND) {
			program.setColor(node.colors);
		} else {
			glVertexAttribPointer(program.aColorData, 3, GL_UNSIGNED_BYTE, true, 3, node.colors.rewind());
			glEnableVertexAttribArray(program.aColorData);
		}

		glDrawArrays(type, 0, node.vertices.capacity() / 3);

		glDisableVertexAttribArray(program.aPosition);
		glDisableVertexAttribArray(program.aColorData);
	}

	public void setOrthographicScale(int scaleX, int scaleY) {
		env.projection = Graphics3D.COMMAND_PARALLEL_SCALE;
		float vw = env.width;
		float vh = env.height;
		float w = vw * (4096.0f / scaleX);
		float h = vh * (4096.0f / scaleY);

		float sx = 2.0f / w;
		float sy = 2.0f / h;
		float sz = 1.0f / 65536.0f;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 0.0f;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] = 0.0f; pm[12] =   tx;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] = 0.0f; pm[13] =   ty;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 0.0f; pm[15] = 1.0f;
	}

	public void setOrthographicW(int w) {
		if (w <= 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PARALLEL_SIZE;
		float vw = env.width;
		float vh = env.height;
		float sx = 2.0f / w;
		float sy = sx * (vw / vh);
		float sz = 1.0f / 65536.0f;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 0.0f;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] = 0.0f; pm[12] =   tx;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] = 0.0f; pm[13] =   ty;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 0.0f; pm[15] = 1.0f;
	}

	public void setOrthographicWH(int w, int h) {
		if (w <= 0 || h <= 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PARALLEL_SIZE;
		float sx = 2.0f / w;
		float sy = 2.0f / h;
		float sz = 1.0f / 65536.0f;
		float tx = 2.0f * env.centerX / env.width - 1.0f;
		float ty = 2.0f * env.centerY / env.height - 1.0f;
		float tz = 0.0f;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] = 0.0f; pm[12] =   tx;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] = 0.0f; pm[13] =   ty;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 0.0f; pm[15] = 1.0f;
	}

	public void setPerspectiveFov(int near, int far, int angle) {
		if (near <= 0 || far <= 0 || near >= far) {
			return;
		}
		angle = MathUtil.clamp(angle, 2, 2046);
		env.projection = Graphics3D.COMMAND_PERSPECTIVE_FOV;
		env.near = near;
		float rd = 1.0f / (near - far);
		float sx = 1.0f / (float) Math.tan(angle * MathUtil.TO_FLOAT * Math.PI);
		float vw = env.width;
		float vh = env.height;
		float sy = sx * (vw / vh);
		float sz = -(far + near) * rd;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 2.0f * far * near * rd;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] =   tx; pm[12] = 0.0f;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] =   ty; pm[13] = 0.0f;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 1.0f; pm[15] = 0.0f;
	}

	public void setPerspectiveW(int near, int far, int w) {
		if (near <= 0 || far <= 0 || near >= far || w <= 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PERSPECTIVE_WH;
		env.near = near;
		float vw = env.width;
		float vh = env.height;

		float rd = 1.0f / (near - far);
		float sx = 2.0f * near / (w * MathUtil.TO_FLOAT);
		float sy = sx * (vw / vh);
		float sz = -(near + far) * rd;
		float tx = 2.0f * env.centerX / vw - 1.0f;
		float ty = 2.0f * env.centerY / vh - 1.0f;
		float tz = 2.0f * far * near * rd;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] =   tx; pm[12] = 0.0f;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] =   ty; pm[13] = 0.0f;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 1.0f; pm[15] = 0.0f;
	}

	public void setPerspectiveWH(float near, float far, int w, int h) {
		if (near <= 0 || far <= 0 || near >= far || w == 0 || h == 0) {
			return;
		}
		env.projection = Graphics3D.COMMAND_PERSPECTIVE_WH;
		env.near = near;
		float width = w * MathUtil.TO_FLOAT;
		float height = h * MathUtil.TO_FLOAT;

		float rd = 1.0f / (near - far);
		float sx = 2.0f * near / width;
		float sy = 2.0f * near / height;
		float sz = -(near + far) * rd;
		float tx = 2.0f * env.centerX / env.width - 1.0f;
		float ty = 2.0f * env.centerY / env.height - 1.0f;
		float tz = 2.0f * far * near * rd;

		float[] pm = env.projMatrix;
		pm[0] =   sx; pm[4] = 0.0f; pm[ 8] =   tx; pm[12] = 0.0f;
		pm[1] = 0.0f; pm[5] =   sy; pm[ 9] =   ty; pm[13] = 0.0f;
		pm[2] = 0.0f; pm[6] = 0.0f; pm[10] =   sz; pm[14] =   tz;
		pm[3] = 0.0f; pm[7] = 0.0f; pm[11] = 1.0f; pm[15] = 0.0f;
	}

	public void setViewTransArray(float[] matrices) {
		env.matrices = matrices;
	}

	private void selectAffineTrans(int n) {
		float[] matrices = env.matrices;
		if (matrices != null && matrices.length >= (n + 1) * 12) {
			System.arraycopy(matrices, n * 12, env.viewMatrix, 0, 12);
		}
	}

	public void setCenter(int cx, int cy) {
		env.centerX = cx;
		env.centerY = cy;
	}

	public void setClearColor(int color) {
		clearColor = color;
	}

	public synchronized void flushToBuffer() {
		if (stack.isEmpty()) {
			return;
		}
		bindEglContext();
		try {
			copy2d(true);
			flushStep = 1;
			for (RenderNode r : stack) {
				r.render(this);
			}
			flushStep = 2;
			for (RenderNode r : stack) {
				r.render(this);
				r.recycle();
			}
			glDisable(GL_BLEND);
			glDepthMask(true);
			glClear(GL_DEPTH_BUFFER_BIT);
			glFlush();
			if (targetTexture != null) {
				glReadPixels(0, 0, 256, 256, GL_RGBA, GL_UNSIGNED_BYTE, targetTexture.image.getRaster());
			} else if (targetGraphics != null) {
				Rect clip = this.gClip;
				
				int width = mBitmapBuffer.getWidth();
				int height = mBitmapBuffer.getHeight();
				int[] data=new int[width*height];
				Utils.glReadPixels(clip.left, clip.top, clip.width(), clip.height(), data, width, width*4);
				for(int i=0;i<width*height;i++)
				{
					data[i]=rgbaToArgb(data[i]);
					//data[i] |= 0xFF000000;
				}
				
				mBitmapBuffer.setRGB(0, 0, width, height, data, 0, width);
			}
		} finally {
			stack.clear();
			releaseEglContext();
		}
	}
	
	public static int rgbaToArgb(int color) { 
		final  int a = ALPHA(color);
		final  int b =  RED(color);
		final  int g = GREEN(color);
		final  int r = BLUE(color); 
		
		/* if(a!=0xff)
		{
			System.out.println("alpha "+a);
		} */
		
		//return ( a<< 24) | ( (g)<< 16) | ((b)<< 8) | (b); //保留 
		return ( a<< 24) | ( r<< 16) | (g<< 8) | (b);  
		//return color;
	}
	
	public static void colorConvert(int[] src, int[] dst) {
		for(int i=0; i<src.length; i++)
		{
			final int argb=src[i];
			final  int a = ALPHA(argb);
			final  int r = RED(argb);
			final  int g = GREEN(argb);
			final  int b = BLUE(argb); 
			
			dst[i]=(( a<< 24) | ( b<< 16) | (g<< 8) | (r));  
		}
	}
	
	
	public static int RED(int argb)
	{	
		return (((argb) >> 16) & 0xFF);
	}
	public static int GREEN(int argb)
	{	
		return (((argb) >>  8) & 0xFF);
	}
	public static int BLUE(int argb)
	{	
		return ( (argb)        & 0xFF);
	}
	public static int ALPHA(int argb)
	{	
		return (((argb) >> 24) & 0xFF);
	}

	static class Environment {
		final TextureImpl[] textures = new TextureImpl[16];
		final Light light = new Light();
		final float[] viewMatrix = new float[12];
		final float[] projMatrix = new float[16];

		int projection;
		float near;
		float[] matrices;
		int centerX;
		int centerY;
		int width;
		int height;
		int toonThreshold;
		int toonHigh;
		int toonLow;
		int attrs;
		int textureIdx;
		int texturesLen;
		TextureImpl specular;

		Environment() {}

		TextureImpl getTexture() {
			if (textureIdx < 0 || textureIdx >= texturesLen) {
				return null;
			}
			return textures[textureIdx];
		}
	}

	private static final class InstanceHolder {
		static final Render instance = new Render();
	}
	
	public class Rect{
		public int left=0;
		public int top=0;
		public int right=0;
		public int bottom=0;
		public final void set(int l, int t, int r, int b)
		{
			left=l;
			top=t;
			right=r;
			bottom=b;
		}
		
		public final int width()
		{
			return right-left;
		}
		
		public final int height()
		{
			return bottom-top;
		}
		
		
		public final boolean intersect(int left, int top, int right, int bottom) {
			if (this.left < right && left < this.right && this.top < bottom && top < this.bottom) {
				if (this.left < left) this.left = left;
				if (this.top < top) this.top = top;
				if (this.right > right) this.right = right;
				if (this.bottom > bottom) this.bottom = bottom;
				return true;
			}
			return false;
		}
		
		public final void setEmpty()
		{
			left = right = top = bottom = 0;
		}
	}
}
