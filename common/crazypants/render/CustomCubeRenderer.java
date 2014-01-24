package crazypants.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.ForgeDirection;
import crazypants.enderio.EnderIO;
import crazypants.util.ForgeDirectionOffsets;
import crazypants.vecmath.Vector2f;
import crazypants.vecmath.Vector3d;
import crazypants.vecmath.Vector3f;
import crazypants.vecmath.Vector4f;
import crazypants.vecmath.Vertex;

public class CustomCubeRenderer {

  private static final Tessellator DEFAULT_TES = Tessellator.instance;

  private static final MockTesselator BUF_TES = new MockTesselator();

  private InnerRenderBlocks rb = null;

  public void renderBlock(IBlockAccess ba, Block par1Block, int par2, int par3, int par4) {
    if(rb == null) {
      rb = new InnerRenderBlocks(ba);
    }
    rb.setRenderBoundsFromBlock(par1Block);
    try {
      rb.setTesselatorEnabled(false);
      BUF_TES.reset();
      rb.renderStandardBlock(par1Block, par2, par3, par4);
    } finally {
      rb.setTesselatorEnabled(true);
    }

  }

  private class InnerRenderBlocks extends RenderBlocks {

    private InnerRenderBlocks(IBlockAccess par1iBlockAccess) {
      super(par1iBlockAccess);
    }

    void setTesselatorEnabled(boolean enabled) {
      if(enabled) {
        Tessellator.instance = DEFAULT_TES;
      } else {
        Tessellator.instance = BUF_TES;
      }
    }

    private void resetTesForFace() {
      int b = BUF_TES.brightness;
      Vector4f col = BUF_TES.color;
      BUF_TES.reset();
      BUF_TES.setBrightness(b);
      if(col != null) {
        BUF_TES.setColorRGBA_F(col.x, col.y, col.z, col.w);
      }
    }

    private void renderFace(ForgeDirection face, Block par1Block, double x, double y, double z, Icon texture) {
      renderFaceToBuffer(face, par1Block, x, y, z, texture);

      boolean forceAllEdges = false;
      boolean translateToXYZ = true;

      List<Vertex> vertices = BUF_TES.getVertices();
      List<ForgeDirection> edges;
      if(forceAllEdges) {
        edges = RenderUtil.getEdgesForFace(face);
      } else {
        edges = RenderUtil.getNonConectedEdgesForFace(blockAccess, (int) x, (int) y, (int) z, face);
      }

      float scaleFactor = 15f / 16f;
      Vector2f uv = new Vector2f();
      texture = EnderIO.blockAlloySmelter.getBlockTextureFromSide(3);

      //for each that needs a border, add a geom for the border and move in the 'centre' of the face
      //so there is no overlap
      for (ForgeDirection edge : edges) {

        //We need to move the 'centre' of the face so it doesn't overlap with the border
        moveCorners(vertices, edge, 1 - scaleFactor, face);

        //Now create the geometry for this edge of the border
        float xLen = 1 - Math.abs(edge.offsetX) * scaleFactor;
        float yLen = 1 - Math.abs(edge.offsetY) * scaleFactor;
        float zLen = 1 - Math.abs(edge.offsetZ) * scaleFactor;
        BoundingBox bb = BoundingBox.UNIT_CUBE.scale(xLen, yLen, zLen);

        List<Vector3d> corners = bb.getCornersForFaceD(face);
        for (Vector3d corner : corners) {
          if(translateToXYZ) {
            corner.x += x;
            corner.y += y;
            corner.z += z;
          }
          corner.x += (float) (edge.offsetX * 0.5) - Math.signum(edge.offsetX) * xLen / 2f;
          corner.y += (float) (edge.offsetY * 0.5) - Math.signum(edge.offsetY) * yLen / 2f;
          corner.z += (float) (edge.offsetZ * 0.5) - Math.signum(edge.offsetZ) * zLen / 2f;
        }

        //move in corners
        Vector3d sideVec = new Vector3d();
        sideVec.cross(ForgeDirectionOffsets.forDir(face), ForgeDirectionOffsets.forDir(edge));
        moveCornerVec(corners, sideVec, scaleFactor, face);
        sideVec.negate();
        moveCornerVec(corners, sideVec, scaleFactor, face);

        for (int index = corners.size() - 1; index >= 0; index--) {
          Vector3d corner = corners.get(index);
          if(translateToXYZ) {
            RenderUtil.getUvForCorner(uv, corner, (int) x, (int) y, (int) z, face, texture);
          } else {
            RenderUtil.getUvForCorner(uv, corner, 0, 0, 0, face, texture);
          }
          DEFAULT_TES.setBrightness(vertices.get(index).brightness);
          Vector4f col = vertices.get(index).getColor();
          if(col != null) {
            DEFAULT_TES.setColorRGBA_F(col.x, col.y, col.z, col.w);
          }
          DEFAULT_TES.addVertexWithUV(corner.x, corner.y, corner.z, uv.x, uv.y);
        }
      }

      List<Vertex> cornerVerts = new ArrayList<Vertex>();
      List<ForgeDirection> allEdges = RenderUtil.getEdgesForFace(face);
      for (int i = 0; i < allEdges.size(); i++) {
        ForgeDirection dir = allEdges.get(i);
        ForgeDirection dir2 = i + 1 < allEdges.size() ? allEdges.get(i + 1) : allEdges.get(0);
        if(needsCorner(dir, dir2, edges, face, par1Block, x, y, z)) {

          Vertex v = new Vertex();
          v.uv = new Vector2f();
          v.xyz.set(ForgeDirectionOffsets.forDir(dir));
          v.xyz.x = v.xyz.x == 0 ? dir2.offsetX : v.xyz.x;
          v.xyz.y = v.xyz.y == 0 ? dir2.offsetY : v.xyz.y;
          v.xyz.z = v.xyz.z == 0 ? dir2.offsetZ : v.xyz.z;

          v.xyz.x = Math.max(0, v.xyz.x);
          v.xyz.y = Math.max(0, v.xyz.y);
          v.xyz.z = Math.max(0, v.xyz.z);

          if(ForgeDirectionOffsets.isPositiveOffset(face)) {
            v.xyz.add(ForgeDirectionOffsets.forDir(face));
          }

          if(translateToXYZ) {
            v.xyz.x += x;
            v.xyz.y += y;
            v.xyz.z += z;
            RenderUtil.getUvForCorner(v.uv, v.xyz, (int) x, (int) y, (int) z, face, texture);
          } else {
            RenderUtil.getUvForCorner(v.uv, v.xyz, 0, 0, 0, face, texture);
          }
          cornerVerts.add(v);

          Vector3d corner = new Vector3d(v.xyz);
          if(ForgeDirectionOffsets.isPositiveOffset(face)) {
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir2, null, corner);
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir, dir2, corner);
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir, null, corner);
          } else {
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir, null, corner);
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir, dir2, corner);
            addVertexForCorner(face, x, y, z, texture, translateToXYZ, cornerVerts, dir2, null, corner);
          }

        }
      }

      //TODO: Combine two lists to one once its all working
      //Now render the centre face 
      RenderUtil.addVerticesToTessellator(cornerVerts, DEFAULT_TES);
      RenderUtil.addVerticesToTessellator(vertices, DEFAULT_TES);
    }

    private void addVertexForCorner(ForgeDirection face, double x, double y, double z, Icon texture, boolean translateToXYZ, List<Vertex> vertices,
        ForgeDirection dir, ForgeDirection dir2, Vector3d corner) {
      float scale = 1 / 16f;
      Vertex v = new Vertex();
      v.uv = new Vector2f();
      v.xyz.set(corner);
      v.xyz.sub(ForgeDirectionOffsets.offsetScaled(dir, scale));
      if(dir2 != null) {
        v.xyz.sub(ForgeDirectionOffsets.offsetScaled(dir2, scale));
      }
      if(translateToXYZ) {
        RenderUtil.getUvForCorner(v.uv, v.xyz, (int) x, (int) y, (int) z, face, texture);
      } else {
        RenderUtil.getUvForCorner(v.uv, v.xyz, 0, 0, 0, face, texture);
      }
      //cornerVerts.add(v);
      vertices.add(v);
    }

    private boolean needsCorner(ForgeDirection dir, ForgeDirection dir2, List<ForgeDirection> edges, ForgeDirection face, Block par1Block, double x, double y,
        double z) {
      return edges.contains(dir) || edges.contains(dir2);
    }

    private void moveCornerVec(List<Vector3d> corners, Vector3d edge, float scaleFactor, ForgeDirection face) {
      int[] indices = getClosestVec(edge, corners, face);
      corners.get(indices[0]).x -= scaleFactor * edge.x;
      corners.get(indices[1]).x -= scaleFactor * edge.x;
      corners.get(indices[0]).y -= scaleFactor * edge.y;
      corners.get(indices[1]).y -= scaleFactor * edge.y;
      corners.get(indices[0]).z -= scaleFactor * edge.z;
      corners.get(indices[1]).z -= scaleFactor * edge.z;
    }

    private void moveCorners(List<Vertex> vertices, ForgeDirection edge, float scaleFactor, ForgeDirection face) {
      int[] indices = getClosest(edge, vertices, face);
      vertices.get(indices[0]).xyz.x -= scaleFactor * edge.offsetX;
      vertices.get(indices[1]).xyz.x -= scaleFactor * edge.offsetX;
      vertices.get(indices[0]).xyz.y -= scaleFactor * edge.offsetY;
      vertices.get(indices[1]).xyz.y -= scaleFactor * edge.offsetY;
      vertices.get(indices[0]).xyz.z -= scaleFactor * edge.offsetZ;
      vertices.get(indices[1]).xyz.z -= scaleFactor * edge.offsetZ;
    }

    private int[] getClosest(ForgeDirection edge, List<Vertex> vertices, ForgeDirection face) {
      int[] res = new int[] { -1, -1 };
      boolean highest = edge.offsetX > 0 || edge.offsetY > 0 || edge.offsetZ > 0;
      double minMax = highest ? -Double.MAX_VALUE : Double.MAX_VALUE;
      int index = 0;
      for (Vertex v : vertices) {
        double val = get(v.xyz, edge);
        if(highest ? val >= minMax : val <= minMax) {
          if(val != minMax) {
            res[0] = index;
          } else {
            res[1] = index;
          }
          minMax = val;
        }
        index++;
      }
      return res;
    }

    private int[] getClosestVec(Vector3d edge, List<Vector3d> corners, ForgeDirection face) {
      int[] res = new int[] { -1, -1 };
      boolean highest = edge.x > 0 || edge.y > 0 || edge.z > 0;
      double minMax = highest ? -Double.MAX_VALUE : Double.MAX_VALUE;
      int index = 0;
      for (Vector3d v : corners) {
        double val = get(v, edge);
        if(highest ? val >= minMax : val <= minMax) {
          if(val != minMax) {
            res[0] = index;
          } else {
            res[1] = index;
          }
          minMax = val;
        }
        index++;
      }
      return res;
    }

    private double get(Vector3d xyz, ForgeDirection edge) {
      if(edge == ForgeDirection.EAST || edge == ForgeDirection.WEST) {
        return xyz.x;
      }
      if(edge == ForgeDirection.UP || edge == ForgeDirection.DOWN) {
        return xyz.y;
      }
      return xyz.z;
    }

    private double get(Vector3d xyz, Vector3d edge) {
      if(Math.abs(edge.x) > 0.5) {
        return xyz.x;
      }
      if(Math.abs(edge.y) > 0.5) {
        return xyz.y;
      }
      return xyz.z;
    }

    private void renderFaceToBuffer(ForgeDirection face, Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      setTesselatorEnabled(false);
      resetTesForFace();
      switch (face) {
      case DOWN:
        super.renderFaceYNeg(par1Block, par2, par4, par6, par8Icon);
        break;
      case EAST:
        super.renderFaceXPos(par1Block, par2, par4, par6, par8Icon);
        break;
      case NORTH:
        super.renderFaceZNeg(par1Block, par2, par4, par6, par8Icon);
        break;
      case SOUTH:
        super.renderFaceZPos(par1Block, par2, par4, par6, par8Icon);
        break;
      case UP:
        super.renderFaceYPos(par1Block, par2, par4, par6, par8Icon);
        break;
      case WEST:
        super.renderFaceXNeg(par1Block, par2, par4, par6, par8Icon);
        break;
      case UNKNOWN:
      default:
        break;
      }

    }

    @Override
    public void renderFaceYNeg(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      renderFace(ForgeDirection.DOWN, par1Block, par2, par4, par6, par8Icon);
    }

    @Override
    public void renderFaceYPos(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      resetTesForFace();
      super.renderFaceYPos(par1Block, par2, par4, par6, par8Icon);
      renderFace(ForgeDirection.UP, par1Block, par2, par4, par6, par8Icon);
    }

    @Override
    public void renderFaceZNeg(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      resetTesForFace();
      super.renderFaceZNeg(par1Block, par2, par4, par6, par8Icon);
      renderFace(ForgeDirection.NORTH, par1Block, par2, par4, par6, par8Icon);
    }

    @Override
    public void renderFaceZPos(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      resetTesForFace();
      super.renderFaceZPos(par1Block, par2, par4, par6, par8Icon);
      renderFace(ForgeDirection.SOUTH, par1Block, par2, par4, par6, par8Icon);
    }

    @Override
    public void renderFaceXNeg(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      resetTesForFace();
      super.renderFaceXNeg(par1Block, par2, par4, par6, par8Icon);
      renderFace(ForgeDirection.WEST, par1Block, par2, par4, par6, par8Icon);
    }

    @Override
    public void renderFaceXPos(Block par1Block, double par2, double par4, double par6, Icon par8Icon) {
      resetTesForFace();
      super.renderFaceXPos(par1Block, par2, par4, par6, par8Icon);
      renderFace(ForgeDirection.EAST, par1Block, par2, par4, par6, par8Icon);
    }

  }

  private static class MockTesselator extends Tessellator {

    private final List<Vertex> vertices = new ArrayList<Vertex>();

    private boolean hasTexture;
    private double textureU;
    private double textureV;
    private boolean hasBrightness;
    private int brightness;
    private boolean hasColor;
    private Vector4f color;
    private boolean hasNormals;
    private Vector3f normal;

    public List<Vertex> getVertices() {
      return vertices;
    }

    @Override
    public void setTextureUV(double par1, double par3) {
      super.setTextureUV(par1, par3);
      this.hasTexture = true;
      this.textureU = par1;
      this.textureV = par3;
    }

    @Override
    public void setBrightness(int par1) {
      super.setBrightness(par1);
      this.hasBrightness = true;
      this.brightness = par1;
    }

    /**
     * Sets the RGBA values for the color. Also clamps them to 0-255.
     */
    @Override
    public void setColorRGBA(int r, int g, int b, int a) {
      super.setColorRGBA(r, g, b, a);
      hasColor = true;
      color = ColorUtil.toFloat(new Color(r, g, b, a));
    }

    /**
     * Sets the normal for the current draw call.
     */
    @Override
    public void setNormal(float par1, float par2, float par3) {
      super.setNormal(par1, par2, par3);
      hasNormals = true;
      normal = new Vector3f(par1, par2, par3);
    }

    public void reset() {
      hasNormals = false;
      this.hasColor = false;
      this.hasTexture = false;
      this.hasBrightness = false;
      vertices.clear();
    }

    @Override
    public void addVertex(double x, double y, double z) {
      Vertex v = new Vertex();
      v.setXYZ(x, y, z);
      if(hasTexture) {
        v.setUV(textureU, textureV);
      }
      if(hasBrightness) {
        v.setBrightness(brightness);
      }
      if(this.hasColor) {
        v.setColor(this.color);
      }
      if(hasNormals) {
        v.setNormal(normal);
      }
      vertices.add(v);
    }

  }

}