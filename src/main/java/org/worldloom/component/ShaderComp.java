package org.worldloom.component;

import com.artemis.Entity;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import org.worldloom.component.parent.SerializeComponent;
import org.worldloom.manager.ReflectionManager;
import org.worldloom.manager.ShaderManager;
import org.worldloom.serialize.SerializeParam;
import org.worldloom.serialize.data.EntityDatum;
import org.worldloom.shader.ShaderUniforms;

/** 实体独占的着色程序；重载、删除及世界退出时释放，不拥有源码和源纹理。 */
public class ShaderComp extends SerializeComponent implements Disposable {
    /** 顶点着色器源码路径，不含.vert后缀。 */
    @SerializeParam
    public String vertexName;
    /** 片元着色器源码路径，不含.frag后缀。 */
    @SerializeParam
    public String fragmentName;
    /** 可选的ShaderUniforms子类简称；留空时只使用着色程序。 */
    @SerializeParam
    public String uniformSimpleName;

    public ShaderProgram shaderProgram;
    public ShaderUniforms shaderUniforms;

    /** 重建自有程序；任一加载、编译或回调构造失败都清理半成品并回退普通绘制。 */
    @Override
    public void reload(World world, EntityDatum entityDatum) {
        dispose();
        super.reload(world, entityDatum);
        ShaderManager shaderManager = ShaderManager.getInstance();
        String vertexContext = shaderManager.getVertexContext(vertexName);
        String fragmentContext = shaderManager.getFragmentContext(fragmentName);

        if (vertexContext == null || fragmentContext == null){
            shaderProgram = null;
            return;
        }
        try {
            shaderProgram = new ShaderProgram(vertexContext,fragmentContext);
            if (!shaderProgram.isCompiled()) {
                Gdx.app.error(getTag(),"Could not compile shader: "+shaderProgram.getLog());
                dispose();
                return;
            }
            if (uniformSimpleName == null || uniformSimpleName.isEmpty()) return;
            ReflectionManager reflectionManager = ReflectionManager.getInstance();
            Class<? extends ShaderUniforms> aClass = reflectionManager.getSubTypesOfWithGame(ShaderUniforms.class)
                .stream()
                .filter(c -> c.getSimpleName().equals(uniformSimpleName))
                .findFirst()
                .orElse(null);

            if (aClass == null) {
                Gdx.app.error(getTag(), "Could not find ShaderUniforms for " + uniformSimpleName);
                dispose();
                return;
            }
            shaderUniforms = reflectionManager.createObject(
                aClass,
                new Class[]{Entity.class},
                new Entity[]{world.getEntity(entityId)}
        );

            if (shaderUniforms == null) {
                Gdx.app.error(getTag(), "Could not create ShaderUniforms: " + uniformSimpleName);
                dispose();
                return;
            }
            Gdx.app.debug(getTag(), "Loaded shaderUniforms : " + shaderUniforms);
        } catch (RuntimeException error) {
            dispose();
            Gdx.app.error(getTag(), "Failed to initialize entity shader", error);
        }
    }

    /** 幂等释放组件独占的GL程序；先清引用，重复删除或清理失败均不二次释放。 */
    @Override public void dispose() {
        ShaderProgram previous = shaderProgram;
        shaderProgram = null;
        shaderUniforms = null;
        if (previous != null) previous.dispose();
    }
}
