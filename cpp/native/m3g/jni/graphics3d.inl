/*
* Copyright (c) 2009 Nokia Corporation and/or its subsidiary(-ies).
* All rights reserved.
* This component and the accompanying materials are made available
* under the terms of "Eclipse Public License v1.0"
* which accompanies this distribution, and is available
* at the URL "http://www.eclipse.org/legal/epl-v10.html".
*
* Initial Contributors:
* Nokia Corporation - initial contribution.
*
* Contributors:
*
* Description:
*
*/
#include "javax_microedition_m3g_Graphics3D.h"
// #include <android/log.h>
// #include <android/bitmap.h>

// #define  LOG_TAG    "M3G"
// #define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#if defined(USE_GBM)
#include <gbm.h>
#include <fcntl.h>
struct gbm_device *gbm;
//struct gbm_surface *surface;
int g_device_fd;
#endif


EGLDisplay g_display=NULL;


void* pixels_ptr=NULL;


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
	JNIEnv* env = NULL;
 
    if(vm->GetEnv((void**) &env, JNI_VERSION_10) != JNI_OK) {
       return -1;
    }
 
	if(env == NULL)
	{
		return -1;
	}
	
	if(g_display==NULL)
	{
		#if defined(USE_GBM)
		g_device_fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC | O_NOCTTY | O_NONBLOCK);
		gbm = gbm_create_device (g_device_fd);
		g_display = eglGetDisplay(gbm);
		#else
		g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);	
		#endif

		if(g_display == EGL_NO_DISPLAY)								//失败返回 EGL_NO_DISPLAY
		{
			printf("[native]  Init g_Display Failed\n");
		}
		else
		{
			printf("[native]  Init g_Display Success\n");
		}
	}

    return JNI_VERSION_10;
}


/*
 * Must be executed in UI thread
 */
JNIEXPORT jboolean JNICALL Java_javax_microedition_m3g_Graphics3D__1isProperRenderer
(JNIEnv* /*aEnv*/, jclass)
{
/*    EGLContext ctx;
    EGLConfig config;
    EGLSurface surf;
    EGLint attrib[5];
    EGLint numConfigs;
    bool isProperRenderer;

    // initialize EGL and create a temporary surface & context for reading
    // the renderer string
    eglInitialize(g_display, NULL, NULL);

    attrib[0] = EGL_SURFACE_TYPE;
    attrib[1] = EGL_PBUFFER_BIT;
    attrib[2] = EGL_NONE;

    eglChooseConfig(g_display, attrib, &config, 1, &numConfigs);

    ctx = eglCreateContext(g_display, config, NULL, NULL);

    attrib[0] = EGL_WIDTH;
    attrib[1] = 2;
    attrib[2] = EGL_HEIGHT;
    attrib[3] = 2;
    attrib[4] = EGL_NONE;

    surf = eglCreatePbufferSurface(g_display, config, attrib);
    eglMakeCurrent(g_display, surf, surf, ctx);

    // We check if proper renderer is used and return value which is stored
    // on java side and passed to fuctions where is decided if m3g renders
    // into mutable off-screen image or into framebuffer (see
    // Java_javax_microedition_m3g_Graphics3D__1bindGraphics and
    // releaseGraphicsTarget).
    const GLubyte *info;
    info = glGetString(GL_RENDERER);   // get the renderer string

    // check if "MBX" substring is found
    if ( !info ||  strstr((const char *)info, "MBX"))
    {
        // HW renderer detected.
        // If "MBX" HW is detected we must reset alpha for mutable off-screen
        // images by hand (see releaseGraphicsTarget).
        isProperRenderer = false;
    }
    else
    {
        // Other renderers can use m3g core API m3gSetAlphaWrite without
        // performance drop.
        isProperRenderer = true;
    }

    // destroy the temporary surface & context
    eglMakeCurrent(g_display, NULL, NULL, NULL);
    eglDestroySurface(g_display, surf);
    eglDestroyContext(g_display, ctx);

    return isProperRenderer;*/
    return false;
}

/*
 * Must be executed in UI thread
 */
JNIEXPORT jboolean JNICALL Java_javax_microedition_m3g_Graphics3D__1bindGraphics
(JNIEnv* aEnv, jclass, jlong aCtx, jlong aSurfaceHandle, jint aWidth, jint aHeight,
 jint aClipX, jint aClipY, jint aClipW, jint aClipH,
 jboolean aDepth, jint aHintBits, jboolean aIsProperRenderer, jobject bitmap /* jintArray bitmap */)
{
    M3GRenderContext ctx = (M3GRenderContext)aCtx;

    jboolean isImageTarget = false; /*cmidGraphics->IsImageTarget();*/

    M3G_DO_LOCK

    /*
    * Get the physical screen size and pass it to m3gcore. This affects (improves) the performance
    * as the canvas frambuffer (rendering target) is larger than the physical screen size in
    * devices that have more than one screen orientation, causing extra copying operations.
    *
    * This will improve m3g performance and suppresses extra bitmap copying.
    */

    //TRect screenRect = CCoeEnv::Static()->ScreenDevice()->SizeInPixels();

    //eglWaitNative(EGL_CORE_NATIVE_ENGINE);

    if (m3gSetRenderBuffers((M3GRenderContext)aCtx, aDepth ?
                            M3G_COLOR_BUFFER_BIT|M3G_DEPTH_BUFFER_BIT :
                            M3G_COLOR_BUFFER_BIT) && m3gSetRenderHints((M3GRenderContext)aCtx, aHintBits))
    {
        //int ret = AndroidBitmap_lockPixels(aEnv, bitmap, &pixels_ptr);
		
		//jboolean isCopy = JNI_FALSE;
		
		pixels_ptr = static_cast<void *>(aEnv->GetDirectBufferAddress(bitmap));
		
		//printf("[native] pixels_ptr:%lx\n",pixels_ptr);
		
		//pixels_ptr = (void*)(aEnv->GetIntArrayElements(bitmap, NULL));
		/* 
		isCopy被这个函数设置，将该参数设置成指向 JNI_TRUE 的指针 : 将 int 数组数据拷贝到一个新的内存空间中 , 并将该内存空间首地址返回 ;
		将该参数设置成指向 JNI_FALSE 的指针 : 直接使用 java 中的 int 数组地址 , 返回 java 中的 int 数组的首地址 ;
		将该参数设置成 NULL ( 推荐 ) : 表示不关心如何实现 , 让系统自动选择指针生成方式 , 一般情况下都不关心该生成方式 ; */
		
		
		//aEnv->GetIntArrayRegion(bitmap, 0, aWidth*aHeight, (jint *)pixels_ptr);
		
		
	    m3gBindMemoryTarget((M3GRenderContext)aCtx, pixels_ptr, aWidth, aHeight, M3G_RGB8_32, (M3Guint)(aWidth * 4), 0);
	    //glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);

        m3gSetClipRect((M3GRenderContext)aCtx, aClipX, aClipY, aClipW, aClipH);
        m3gSetViewport((M3GRenderContext)aCtx, aClipX, aClipY, aClipW, aClipH);
    }

    M3G_DO_UNLOCK(aEnv)

    if (isImageTarget && aIsProperRenderer)
    {
        m3gSetAlphaWrite(ctx, M3G_FALSE);
    }

    return isImageTarget;
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1setViewport
(JNIEnv* aEnv, jclass, jlong aHContext, jint aX, jint aY,
 jint aWidth, jint aHeight)
{
    M3G_DO_LOCK
    m3gSetViewport((M3GRenderContext)aHContext, aX, aY, aWidth, aHeight);
    M3G_DO_UNLOCK(aEnv)
}


static void releaseTarget(M3GRenderContext aCtx)
{
    //eglWaitGL();
    m3gReleaseTarget(aCtx);
}


/*
static void releaseGraphicsTarget(M3GRenderContext aCtx, CMIDGraphics *aGraphics,
    TBool aIsImageTarget, TBool aIsProperRenderer )
    {
    releaseTarget(aCtx);

    // clear alpha for only mutable off-screen images (not for canvas/GameCanvas
    // framebuffer) those images are indetified by aIsImageTarget argument
    if (aIsImageTarget)
        {
        if ( aIsProperRenderer )
            {
            m3gSetAlphaWrite(aCtx, M3G_TRUE);
            }
        else
            {
            CFbsBitmap *bitmap = aGraphics->Bitmap();

            const TInt width = bitmap->SizeInPixels().iWidth;
            const TInt height = bitmap->SizeInPixels().iHeight;
            TInt stride = bitmap->ScanLineLength(width, bitmap->DisplayMode());

            bitmap->LockHeap();

            for (TInt i = 0; i < height; i++)
                {
                const void *srcAddr =
                    ((const char *) bitmap->DataAddress()) + i * stride;
                unsigned char *src = (unsigned char *) srcAddr;
                TInt count = width;
                while (count--)
                    {
                    src += 3; //jump to last byte - alpha channel
                    //setting FF to alpha channel for non-canvas image targets
                    *src |= 0xff;
                    src++;
                    }
                }

            bitmap->UnlockHeap();
            }
        }
    }
*/
JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1releaseGraphics
(JNIEnv* aEnv, jclass, jlong aHandle,
 jlong aSurfaceHandle, jboolean /*aIsImageTarget*/, jboolean /*aIsProperRenderer*/ /* , jintArray  bitmap */)
{
    //printf("[native] releaseTarget:%llx\n",aHandle);

    M3G_DO_LOCK
	
	//printf("[native] releaseTarget:%lx\n",(M3GRenderContext)aHandle);

    releaseTarget((M3GRenderContext)aHandle);
	
	
	
	int errorCode = CSynchronization::InstanceL()->GetErrorCode();
	if ( errorCode != 0){
			printf("[native] error:%d %s\n",errorCode,jsr184Exception(errorCode));
	}

    // Release used target surface

    //int ret = AndroidBitmap_unlockPixels(aEnv, bitmap);
	
	//aEnv->ReleaseIntArrayElements(bitmap, (jint *)pixels_ptr, 0);
	/* 
	模式 0 : 刷新 Java 数组 , 释放 C/C++ 数组
	模式 1 ( JNI_COMMIT ) : 刷新 Java 数组 , 不释放 C/C ++ 数组
	模式 2 ( JNI_ABORT ) : 不刷新 Java 数组 , 释放 C/C++ 数组 */
	
	
	//jint len = aEnv->GetArrayLength(bitmap);
	//aEnv->SetIntArrayRegion(bitmap, 0, len, (jint *)pixels_ptr);
	
    pixels_ptr = NULL;
    
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1setCamera
(JNIEnv* aEnv, jclass, jlong aHContext, jlong aHCamera, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (Matrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return;
        }
    }

    M3G_DO_LOCK
    m3gSetCamera((M3GRenderContext) aHContext, (M3GCamera) aHCamera, transform);
    M3G_DO_UNLOCK(aEnv)

    if (transform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, JNI_ABORT);
    }
}

/*
static void renderWorld(M3GRenderContext aHContext,
                        M3GWorld aHWorld)
{
    m3gRenderWorld(aHContext, aHWorld);
}
*/

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1renderWorld
(JNIEnv* aEnv, jclass, jlong aHContext, jlong aHWorld)
{
    M3G_DO_LOCK

    m3gRenderWorld((M3GRenderContext) aHContext, (M3GWorld) aHWorld);

    /*
    CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    eventSource->ExecuteV(&renderWorld,
                       (M3GRenderContext) aHContext,
                       (M3GWorld) aHWorld);
    */
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT jlong JNICALL Java_javax_microedition_m3g_Graphics3D__1ctor
(JNIEnv* aEnv, jclass, jlong aM3g)
{
	printf("[native] sizeof type jlong:%u\n",sizeof(jlong));
    M3G_DO_LOCK
    M3GRenderContext ctx = m3gCreateContext((M3GInterface)aM3g);
	printf("[native] sizeof ctx(Graphics3D):%u %lx %lu\n",sizeof(ctx),ctx, ctx);
	
	printf("[native] sizeof aM3g(Interface):%u %llx %lld\n",sizeof(aM3g),aM3g,aM3g);
	
	printf("[native] sizeof type M3Gpointer:%u\n",sizeof(M3Gpointer));
	
	
    M3G_DO_UNLOCK(aEnv)

    return (uintptr_t)ctx;
}

struct RenderStruct
{
    M3GVertexBuffer hVertices;
    M3GIndexBuffer hIndices;
    M3GAppearance hAppearance;
    const M3GMatrix *transform;
};

/*
static void renderImmediate(M3GRenderContext aHContext, RenderStruct *aR, jint aScope)
{
    m3gRender(aHContext,
              aR->hVertices,
              aR->hIndices,
              aR->hAppearance,
              aR->transform, 1.0f, aScope);
}
*/

/*
 * Must be executed in UI thread
 */

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1render
(JNIEnv* aEnv, jclass, jlong aHContext,
 jlong aHVertices, jlong aHIndices, jlong aHAppearance, jbyteArray aTransform, jint aScope)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)aEnv->GetByteArrayElements(aTransform, NULL);
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return;
        }
    }

    /*
    RenderStruct r;
    r.hVertices = (M3GVertexBuffer) aHVertices;
    r.hIndices = (M3GIndexBuffer) aHIndices;
    r.hAppearance = (M3GAppearance) aHAppearance;
    r.transform = transform;
    */


    M3G_DO_LOCK

    m3gRender((M3GRenderContext) aHContext, (M3GVertexBuffer) aHVertices, (M3GIndexBuffer) aHIndices, (M3GAppearance) aHAppearance,
              transform, 1.0f, aScope);

    /*
      CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    eventSource->ExecuteV(&renderImmediate, ((M3GRenderContext) aHContext), &r, aScope);
    */
    M3G_DO_UNLOCK(aEnv)


    if (transform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, JNI_ABORT);
    }
}

/*
static void clear(M3GRenderContext aHContext, M3GBackground aHBackground)
{
    m3gClear(aHContext, aHBackground);
}
*/

/*
 * Must be executed in UI thread
 */
JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1clear
(JNIEnv* aEnv, jclass, jlong aCtx, jlong aBg)
{
    M3G_DO_LOCK
    m3gClear((M3GRenderContext)aCtx, (M3GBackground)aBg);

    /*
    CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    eventSource->ExecuteV(&clear, (M3GRenderContext)aCtx, (M3GBackground)aBg);
    */
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1releaseImage
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    M3G_DO_LOCK

    releaseTarget((M3GRenderContext)aHCtx);

    /*
    CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    eventSource->ExecuteV(&releaseTarget, (M3GRenderContext)aHCtx);
    */
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1addRef
(JNIEnv* aEnv, jclass, jlong aObject)
{
    M3G_DO_LOCK
    m3gAddRef((M3GObject) aObject);
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1addLight
(JNIEnv* aEnv, jclass, jlong aHContext, jlong aHLight, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return 0;
        }
    }
    M3G_DO_LOCK
    int idx = m3gAddLight((M3GRenderContext) aHContext, (M3GLight) aHLight, transform);
    M3G_DO_UNLOCK(aEnv)

    if (transform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, JNI_ABORT);
    }

    return idx;
}

/*
static void bindImage(M3GRenderContext hCtx, M3GImage hImg, M3Gbool depth, M3Gbitmask hintBits)
{
    if (m3gSetRenderBuffers(hCtx, depth ? M3G_COLOR_BUFFER_BIT|M3G_DEPTH_BUFFER_BIT : M3G_COLOR_BUFFER_BIT) && m3gSetRenderHints(hCtx, hintBits)) {
        m3gBindImageTarget(hCtx, hImg);
    }
}
*/

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1bindImage
(JNIEnv* aEnv, jclass, jlong aHCtx, jlong aImageHandle, jboolean aDepth, jint aHintBits)
{
    M3G_DO_LOCK

    if (m3gSetRenderBuffers((M3GRenderContext)aHCtx, (M3Gbool)aDepth ? M3G_COLOR_BUFFER_BIT|M3G_DEPTH_BUFFER_BIT : M3G_COLOR_BUFFER_BIT) && m3gSetRenderHints((M3GRenderContext)aHCtx, (M3Gbitmask)aHintBits))
    {
        m3gBindImageTarget((M3GRenderContext)aHCtx, (M3GImage)aImageHandle);
    }

    //CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    //eventSource->ExecuteV(&bindImage, (M3GRenderContext)aHCtx, (M3GImage)aImageHandle, (M3Gbool)aDepth, (M3Gbitmask)aHintBits);
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1resetLights
(JNIEnv* aEnv, jclass, jlong aHContext)
{
    M3G_DO_LOCK
    m3gClearLights((M3GRenderContext) aHContext);
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1setDepthRange
(JNIEnv* aEnv, jclass, jlong aHContext, jfloat aDepthNear, jfloat aDepthFar)
{
    M3G_DO_LOCK
    m3gSetDepthRange((M3GRenderContext) aHContext, aDepthNear, aDepthFar);
    M3G_DO_UNLOCK(aEnv)
}

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1setLight
(JNIEnv* aEnv, jclass, jlong aHContext, jint aLightIndex, jlong aHLight, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return;
        }
    }

    M3G_DO_LOCK
    m3gSetLight((M3GRenderContext) aHContext, aLightIndex, (M3GLight) aHLight, transform);
    M3G_DO_UNLOCK(aEnv)

    if (transform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, JNI_ABORT);
    }
}

/*
static void renderNode(M3GRenderContext aHCtx,
                       M3GNode aHNode,
                       const M3GMatrix *aMtx)
{
    m3gRenderNode(aHCtx, aHNode, aMtx);
}
*/

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1renderNode
(JNIEnv* aEnv, jclass, jlong aHCtx, jlong aHNode, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return;
        }
    }

    M3G_DO_LOCK

    m3gRenderNode((M3GRenderContext)aHCtx, (M3GNode)aHNode, (const M3GMatrix *)transform);

    //CJavaM3GEventSource* eventSource = JavaUnhand<CJavaM3GEventSource>(aEventSourceHandle);
    //eventSource->ExecuteV(&renderNode, (M3GRenderContext)aHCtx, (M3GNode)aHNode, (const M3GMatrix *)transform);
    M3G_DO_UNLOCK(aEnv)

    if (aTransform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, JNI_ABORT);
    }
}

#if defined(M3G_ENABLE_PROFILING)

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getStatistics
(JNIEnv* aEnv, jclass, jintArray aStatArray)
{
    const M3Gint *statArray = (M3Gint *)(aStatArray != NULL ? aEnv->GetIntArrayElements(aStatArray, NULL) : NULL);
    jint statArrayLength = aStatArray ? aEnv->GetArrayLength(aStatArray) : 0;

    if (statArray != NULL && statArrayLength >= sizeof(m3gs_statistic))
    {
        m3gCopy((void*)statArray, m3gs_statistic, sizeof(m3gs_statistic));
    }

    M3G_DO_LOCK
    m3gZero(m3gs_statistic, sizeof(m3gs_statistic));
    M3G_DO_UNLOCK(aEnv)

    if (statArray)
    {
        aEnv->ReleaseIntArrayElements(aStatArray, (jint*)statArray, 0);
    }

    return sizeof(m3gs_statistic);
}

#endif /* M3G_ENABLE_PROFILING */



/* M3G 1.1 JNI Calls */

JNIEXPORT void JNICALL Java_javax_microedition_m3g_Graphics3D__1getViewTransform
(JNIEnv* aEnv, jclass, jlong aHCtx, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return;
        }
    }

    M3G_DO_LOCK
    m3gGetViewTransform((M3GRenderContext) aHCtx, transform);
    M3G_DO_UNLOCK(aEnv)

    if (transform)
    {
        /* copy array to Java side and release arrays */
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, 0);
    }
}

JNIEXPORT jlong JNICALL Java_javax_microedition_m3g_Graphics3D__1getCamera
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    M3G_DO_LOCK
    jlong camera = (jlong)m3gGetCamera((M3GRenderContext)aHCtx);
    M3G_DO_UNLOCK(aEnv)

    return camera;
}

JNIEXPORT jlong JNICALL Java_javax_microedition_m3g_Graphics3D__1getLightTransform
(JNIEnv* aEnv, jclass, jlong aHCtx, jint aLightIndex, jbyteArray aTransform)
{
    M3GMatrix *transform = NULL;
    if (aTransform)
    {
        transform = (M3GMatrix *)(aEnv->GetByteArrayElements(aTransform, NULL));
        if (transform == NULL)
        {
            M3G_RAISE_EXCEPTION(aEnv, "java/lang/OutOfMemoryError");
            return 0;
        }
    }
    M3G_DO_LOCK
    jlong lightTransform = (jlong)m3gGetLightTransform((M3GRenderContext)aHCtx, aLightIndex, transform);
    M3G_DO_UNLOCK(aEnv)

    if (transform)
    {
        aEnv->ReleaseByteArrayElements(aTransform, (jbyte*)transform, 0);
    }

    return (jlong)lightTransform;
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getLightCount
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    M3G_DO_LOCK
    jint lightCount = (jint)m3gGetLightCount((M3GRenderContext)aHCtx);
    M3G_DO_UNLOCK(aEnv)

    return lightCount;
}

JNIEXPORT jfloat JNICALL Java_javax_microedition_m3g_Graphics3D__1getDepthRangeNear
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    float depthNear = 0;
    float depthFar = 0;

    M3G_DO_LOCK
    m3gGetDepthRange((M3GRenderContext) aHCtx, &depthNear, &depthFar);
    M3G_DO_UNLOCK(aEnv)

    return (jfloat)depthNear;
}

JNIEXPORT jfloat JNICALL Java_javax_microedition_m3g_Graphics3D__1getDepthRangeFar
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    float depthNear = 0;
    float depthFar = 0;

    M3G_DO_LOCK
    m3gGetDepthRange((M3GRenderContext) aHCtx, &depthNear, &depthFar);
    M3G_DO_UNLOCK(aEnv)

    return (jfloat)depthFar;
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getViewportX
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    int viewport[4];

    M3G_DO_LOCK
    m3gGetViewport((M3GRenderContext)aHCtx, &viewport[0],
                   &viewport[1],
                   &viewport[2],
                   &viewport[3]);
    M3G_DO_UNLOCK(aEnv)

    return (jint)viewport[0];
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getViewportY
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    int viewport[4];

    M3G_DO_LOCK
    m3gGetViewport((M3GRenderContext)aHCtx, &viewport[0],
                   &viewport[1],
                   &viewport[2],
                   &viewport[3]);
    M3G_DO_UNLOCK(aEnv)

    return (jint)viewport[1];
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getViewportWidth
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    int viewport[4];

    M3G_DO_LOCK
    m3gGetViewport((M3GRenderContext)aHCtx, &viewport[0],
                   &viewport[1],
                   &viewport[2],
                   &viewport[3]);
    M3G_DO_UNLOCK(aEnv)

    return (jint)viewport[2];
}

JNIEXPORT jint JNICALL Java_javax_microedition_m3g_Graphics3D__1getViewportHeight
(JNIEnv* aEnv, jclass, jlong aHCtx)
{
    int viewport[4];

    M3G_DO_LOCK
    m3gGetViewport((M3GRenderContext)aHCtx, &viewport[0],
                   &viewport[1],
                   &viewport[2],
                   &viewport[3]);
    M3G_DO_UNLOCK(aEnv)

    return (jint)viewport[3];
}

JNIEXPORT jboolean JNICALL Java_javax_microedition_m3g_Graphics3D__1isAASupported
(JNIEnv* /*aEnv*/, jclass, jlong aM3g)
{
    M3Gbool aaSupport = M3G_FALSE;

    aaSupport = m3gIsAntialiasingSupported((M3GInterface)aM3g);

    return (jboolean)aaSupport;
}
