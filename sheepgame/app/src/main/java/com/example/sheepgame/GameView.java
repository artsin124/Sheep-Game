package com.example.sheepgame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // Debug flag to display hitboxes
    private static final boolean DEBUG_HITBOXES = false;

    // Game loop thread
    private Thread gameThread;
    private volatile boolean running;

    // Render tools
    private SurfaceHolder holder;
    private Paint paint;
    private Paint debugPaint;
    private Random random;

    // Bitmaps
    private Bitmap normalSheep;
    private Bitmap fatSheep;
    private Bitmap gunSheep;
    private Bitmap knifeSheep;
    private Bitmap withoutHeadwoolSheep;
    private Bitmap headwoolBitmap;

    // Audio
    private SoundPool soundPool;
    private int mehSoundId;

    // Screen dimensions
    private int screenWidth;
    private int screenHeight;

    // Sheep positioning
    private float sheepX;
    private float sheepY;
    private float sheepWidth;
    private float sheepHeight;

    private float centerX;
    private float groundY;
    private float baseScale;

    // Headwool anchors and drag states
    private float woolAnchorX;
    private float woolAnchorY;
    private float woolDrawWidth;
    private float woolDrawHeight;

    private boolean isDraggingWool = false;
    private float woolCurrentX;
    private float woolCurrentY;
    private boolean isGlidingBack = false;

    // State Variables
    private enum SheepState {
        NORMAL,
        ANGRY_GUN,
        ANGRY_KNIFE
    }
    private SheepState baseState = SheepState.NORMAL;

    // Hold center state
    private boolean isPressedCenter = false;
    private float currentScale = 1.0f;
    private float targetScale = 1.0f;
    private float angryTimer = 0.0f;

    // Jump state (Physics based)
    private boolean isJumping = false;
    private float jumpOffset = 0.0f; // offset upwards (subtracted from Y)
    private float jumpVelocity = 0.0f;
    private static final float GRAVITY = 2800f; // pixels/sec^2
    private static final float JUMP_FORCE = -1100f; // pixels/sec starting upwards

    // Particles
    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        float life; // 1.0 down to 0.0
        int color;
    }
    private List<Particle> particles = new ArrayList<>();

    // Interaction Hitboxes (relative to sheep bounding box)
    private final RectF headwoolHitbox = new RectF();
    private final RectF leftLegHitbox = new RectF();
    private final RectF rightLegHitbox = new RectF();
    private final RectF centerHitbox = new RectF();

    public GameView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();

        // Load Bitmaps
        normalSheep = BitmapFactory.decodeResource(getResources(), R.drawable.normal_sheep);
        fatSheep = BitmapFactory.decodeResource(getResources(), R.drawable.fat_sheep);
        gunSheep = BitmapFactory.decodeResource(getResources(), R.drawable.gun_sheep);
        knifeSheep = BitmapFactory.decodeResource(getResources(), R.drawable.knife_sheep);
        withoutHeadwoolSheep = BitmapFactory.decodeResource(getResources(), R.drawable.without_headwool_sheep);
        headwoolBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.headwool);

        // Load Sound
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();
        mehSoundId = soundPool.load(context, R.raw.meh, 1);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Dimensions will be available in surfaceChanged, but let's handle initialization there
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenWidth = width;
        screenHeight = height;

        // Position sheep in the middle-lower region
        sheepWidth = screenWidth * 0.65f;
        float sheepAspect = normalSheep.getHeight() / (float) normalSheep.getWidth();
        sheepHeight = sheepWidth * sheepAspect;

        sheepX = (screenWidth - sheepWidth) / 2f;
        sheepY = (screenHeight - sheepHeight) / 2f + screenHeight * 0.05f;

        centerX = screenWidth / 2f;
        groundY = sheepY + sheepHeight;
        baseScale = sheepWidth / (float) normalSheep.getWidth();

        // Scale wool separate sprite proportionally using baseScale
        woolDrawWidth = headwoolBitmap.getWidth() * baseScale;
        woolDrawHeight = headwoolBitmap.getHeight() * baseScale;

        // Define Wool Anchor Point initially
        woolAnchorX = centerX;
        woolAnchorY = groundY - sheepHeight + sheepHeight * 0.25f;

        // Hitboxes will be dynamically updated in update() loop
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Thread will pause/stop
    }

    private void updateHitboxes(float drawX, float drawY, float drawW, float drawH) {
        // Headwool is on top center-right
        headwoolHitbox.set(
                drawX + drawW * 0.35f,
                drawY + drawH * 0.05f,
                drawX + drawW * 0.70f,
                drawY + drawH * 0.35f
        );

        // Legs are at the bottom left/right
        leftLegHitbox.set(
                drawX + drawW * 0.22f,
                drawY + drawH * 0.75f,
                drawX + drawW * 0.44f,
                drawY + drawH * 1.0f
        );

        rightLegHitbox.set(
                drawX + drawW * 0.56f,
                drawY + drawH * 0.75f,
                drawX + drawW * 0.78f,
                drawY + drawH * 1.0f
        );

        // Center body is the core region
        centerHitbox.set(
                drawX + drawW * 0.28f,
                drawY + drawH * 0.32f,
                drawX + drawW * 0.72f,
                drawY + drawH * 0.75f
        );
    }

    public void resume() {
        running = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    public void pause() {
        running = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();

        while (running) {
            if (!holder.getSurface().isValid()) {
                continue;
            }

            long now = System.nanoTime();
            double dt = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            // Cap dt to prevent massive physics jumps
            if (dt > 0.1) {
                dt = 0.1;
            }

            update((float) dt);
            drawGame();

            // Frame capping (~60 FPS)
            long elapsedMs = (System.nanoTime() - now) / 1_000_000;
            long sleepTime = 16 - elapsedMs;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
    }

    private void update(float dt) {
        // Determine active bitmap for dynamic positioning and hitboxes
        Bitmap activeBitmap;
        if (isPressedCenter) {
            activeBitmap = fatSheep;
        } else if (isDraggingWool || isGlidingBack) {
            activeBitmap = withoutHeadwoolSheep;
        } else {
            switch (baseState) {
                case ANGRY_GUN:
                    activeBitmap = gunSheep;
                    break;
                case ANGRY_KNIFE:
                    activeBitmap = knifeSheep;
                    break;
                case NORMAL:
                default:
                    activeBitmap = normalSheep;
                    break;
            }
        }

        float drawW = activeBitmap.getWidth() * baseScale;
        float drawH = activeBitmap.getHeight() * baseScale;
        float drawX = centerX - drawW / 2f;
        float drawY = groundY - drawH - jumpOffset; // Moves hitboxes and anchors with jump

        // Update hitboxes dynamically based on currently active sprite and jump
        updateHitboxes(drawX, drawY, drawW, drawH);

        // Update Wool Anchor point dynamically (moves with jump and matches withoutHeadwoolSheep)
        woolAnchorX = drawX + drawW * 0.50f;
        woolAnchorY = drawY + drawH * 0.22f;

        // Update Angry Timer
        if (angryTimer > 0f) {
            angryTimer -= dt;
            if (angryTimer <= 0f) {
                baseState = SheepState.NORMAL;
            }
        }

        // 1. Lerp Scale (for press and hold center)
        targetScale = isPressedCenter ? 1.28f : 1.0f;
        float scaleLerpFactor = Math.min(1.0f, 12.0f * dt);
        currentScale += (targetScale - currentScale) * scaleLerpFactor;

        // 2. Headwool Gliding Back Physics
        if (isGlidingBack) {
            float lerpFactor = Math.min(1.0f, 16.0f * dt);
            woolCurrentX += (woolAnchorX - woolCurrentX) * lerpFactor;
            woolCurrentY += (woolAnchorY - woolCurrentY) * lerpFactor;

            // Check if arrived at anchor point
            float dx = woolCurrentX - woolAnchorX;
            float dy = woolCurrentY - woolAnchorY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 10.0f) {
                // Snap to anchor
                woolCurrentX = woolAnchorX;
                woolCurrentY = woolAnchorY;
                isGlidingBack = false;

                // Pick angry state randomly
                baseState = random.nextBoolean() ? SheepState.ANGRY_GUN : SheepState.ANGRY_KNIFE;
                angryTimer = 2.0f; // Stay angry for 2 seconds

                // Play sound
                soundPool.play(mehSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        }

        // 3. Jump Physics
        if (isJumping) {
            jumpVelocity += GRAVITY * dt;
            jumpOffset -= jumpVelocity * dt; // offset upwards

            // Land detection
            if (jumpOffset <= 0.0f) {
                jumpOffset = 0.0f;
                jumpVelocity = 0.0f;
                isJumping = false;
                triggerLandEffects();
            }
        }

        // 4. Update Particles
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= (float) Math.pow(0.1, dt); // Drag
            p.vy *= (float) Math.pow(0.1, dt);
            p.life -= dt * 2.0f; // Life decays in 0.5s
            if (p.life <= 0f) {
                iterator.remove();
            }
        }
    }

    private void triggerLandEffects() {
        // Spawn puff particles at the feet of the sheep
        float feetX = centerX;
        float feetY = groundY - sheepHeight * 0.05f;

        for (int i = 0; i < 14; i++) {
            Particle p = new Particle();
            p.x = feetX + (random.nextFloat() - 0.5f) * (sheepWidth * 0.4f);
            p.y = feetY + (random.nextFloat() - 0.5f) * 15f;
            p.vx = (random.nextFloat() - 0.5f) * 320f;
            p.vy = -random.nextFloat() * 120f - 40f;
            p.size = random.nextFloat() * 15f + 8f;
            p.life = 1.0f;
            // Soft warm cream grey particles
            p.color = Color.argb(180, 220, 215, 205);
            particles.add(p);
        }
    }

    private void drawGame() {
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }

        try {
            // Draw premium minimal background (warm soft cream gradient)
            canvas.drawColor(Color.parseColor("#FAF8F5"));

            // Draw decorative grid/lines or subtle decorations to look premium
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.parseColor("#EFECE6"));
            // Horizontal line near bottom
            canvas.drawLine(0, groundY - sheepHeight * 0.05f, screenWidth, groundY - sheepHeight * 0.05f, paint);

            // Draw shadow under the sheep
            drawShadow(canvas);

            // Draw particles
            drawParticles(canvas);

            // Draw Sheep Sprite
            drawSheep(canvas);

            // Draw UI Overlays (Title, Interactive Instructions)
            drawUI(canvas);

            // Draw debug hitboxes if enabled
            if (DEBUG_HITBOXES) {
                drawDebugHitboxes(canvas);
            }

        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawShadow(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        
        // Base shadow configuration at the center and ground level
        float shadowCenterX = centerX;
        float shadowCenterY = groundY - sheepHeight * 0.06f;
        
        // Scale shadow with jump height and fat scale
        float maxJumpHeight = 250f;
        float jumpFactor = Math.min(jumpOffset / maxJumpHeight, 1.0f);
        
        float scaleX = currentScale * (1.0f - jumpFactor * 0.5f);
        float scaleY = (1.0f - jumpFactor * 0.7f);
        
        float rx = sheepWidth * 0.32f * scaleX;
        float ry = sheepHeight * 0.05f * scaleY;

        int shadowAlpha = (int) (35 * (1.0f - jumpFactor * 0.75f));
        paint.setColor(Color.argb(shadowAlpha, 40, 30, 20));

        canvas.drawOval(
                shadowCenterX - rx,
                shadowCenterY - ry,
                shadowCenterX + rx,
                shadowCenterY + ry,
                paint
        );
    }

    private void drawParticles(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            int alpha = (int) (p.life * Color.alpha(p.color));
            paint.setColor(Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            canvas.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * p.life), paint);
        }
    }

    private void drawSheep(Canvas canvas) {
        // Determine active bitmap
        Bitmap activeBitmap;
        if (isPressedCenter) {
            activeBitmap = fatSheep;
        } else if (isDraggingWool || isGlidingBack) {
            activeBitmap = withoutHeadwoolSheep;
        } else {
            switch (baseState) {
                case ANGRY_GUN:
                    activeBitmap = gunSheep;
                    break;
                case ANGRY_KNIFE:
                    activeBitmap = knifeSheep;
                    break;
                case NORMAL:
                default:
                    activeBitmap = normalSheep;
                    break;
            }
        }

        // Calculate dynamic dimensions to respect aspect ratios and ground alignment
        float drawW = activeBitmap.getWidth() * baseScale;
        float drawH = activeBitmap.getHeight() * baseScale;
        float drawX = centerX - drawW / 2f;
        float drawY = groundY - drawH - jumpOffset;

        canvas.save();

        // Center of scaling is the middle of the body
        float scaleCenterX = drawX + drawW / 2f;
        float scaleCenterY = drawY + drawH * 0.65f;

        // Apply scale matrices for the body swell (fat sheep hold)
        float widthScale = currentScale;
        float heightScale = 1.0f / (float) Math.sqrt(currentScale);
        canvas.scale(widthScale, heightScale, scaleCenterX, scaleCenterY);

        // Reset paint color/opacity to prevent state leakage (transparency)
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);

        // Draw sheep body
        RectF sheepRect = new RectF(drawX, drawY, drawX + drawW, drawY + drawH);
        canvas.drawBitmap(activeBitmap, null, sheepRect, paint);

        canvas.restore();

        // Draw separate headwool if dragging or gliding back
        // Drawn outside the body's scale transformation to remain directly under finger
        if (isDraggingWool || isGlidingBack) {
            float drawXWool = woolCurrentX;
            float drawYWool = woolCurrentY;

            // Reset paint color/opacity again for the headwool bitmap
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);

            // Draw headwool centered at (drawXWool, drawYWool)
            RectF woolRect = new RectF(
                    drawXWool - woolDrawWidth / 2f,
                    drawYWool - woolDrawHeight / 2f,
                    drawXWool + woolDrawWidth / 2f,
                    drawYWool + woolDrawHeight / 2f
                );
            canvas.drawBitmap(headwoolBitmap, null, woolRect, paint);
        }
    }

    private void drawUI(Canvas canvas) {
        // Draw elegant Title text
        paint.setColor(Color.parseColor("#4A3E3D"));
        paint.setTextSize(64f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("Sheep Game", screenWidth / 2f, screenHeight * 0.12f, paint);

        // Draw elegant Instruction subtitle
        paint.setColor(Color.parseColor("#8E807E"));
        paint.setTextSize(36f);
        paint.setFakeBoldText(false);
        canvas.drawText("Press center to swell • Drag wool to trigger angry", screenWidth / 2f, screenHeight * 0.16f, paint);
        canvas.drawText("Tap legs to jump", screenWidth / 2f, screenHeight * 0.19f, paint);

        // Draw status label if angry
        if (baseState != SheepState.NORMAL && !isPressedCenter && !isDraggingWool && !isGlidingBack) {
            paint.setColor(Color.parseColor("#C25953"));
            paint.setTextSize(40f);
            paint.setFakeBoldText(true);
            canvas.drawText("ANGRY!", screenWidth / 2f, groundY - sheepHeight - 20f - jumpOffset, paint);
        }
    }

    private void drawDebugHitboxes(Canvas canvas) {
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(5f);

        // Headwool hitbox: Red
        debugPaint.setColor(Color.RED);
        canvas.drawRect(headwoolHitbox, debugPaint);

        // Left leg hitbox: Green
        debugPaint.setColor(Color.GREEN);
        canvas.drawRect(leftLegHitbox, debugPaint);

        // Right leg hitbox: Green
        debugPaint.setColor(Color.GREEN);
        canvas.drawRect(rightLegHitbox, debugPaint);

        // Center hitbox: Blue
        debugPaint.setColor(Color.BLUE);
        canvas.drawRect(centerHitbox, debugPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check hitbox touches in priority order

                // 1. Headwool Hitbox
                if (headwoolHitbox.contains(tx, ty)) {
                    isDraggingWool = true;
                    woolCurrentX = tx;
                    woolCurrentY = ty;
                    isPressedCenter = false;
                    isGlidingBack = false;
                    return true;
                }

                // 2. Leg Hitboxes (Tap to Jump)
                if (leftLegHitbox.contains(tx, ty) || rightLegHitbox.contains(tx, ty)) {
                    if (!isJumping) {
                        isJumping = true;
                        jumpVelocity = JUMP_FORCE;
                    }
                    return true;
                }

                // 3. Center Body Hitbox (Hold to swell)
                if (centerHitbox.contains(tx, ty)) {
                    isPressedCenter = true;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingWool) {
                    woolCurrentX = tx;
                    woolCurrentY = ty;
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingWool) {
                    isDraggingWool = false;
                    isGlidingBack = true;
                }
                if (isPressedCenter) {
                    isPressedCenter = false;
                    // Revert to Normal on releasing the swell hold
                    baseState = SheepState.NORMAL;
                    angryTimer = 0f;
                }
                break;
        }

        return super.onTouchEvent(event);
    }
}
