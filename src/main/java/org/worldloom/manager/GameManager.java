package org.worldloom.manager;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ObjectMap;
import java.util.Objects;

/**
 * @Auther WenLong
 * @Date 2025/6/17 11:11
 * @Description 游戏管理器
 **/
public class GameManager {
    private final static String TAG = GameManager.class.getSimpleName();
    private static ObjectMap<Class<? extends Game> , Game> table;
    private static Game current;

    /** 延迟建立应用类型索引，注册与查询均在宿主主线程调用。 */
    private static ObjectMap<Class<? extends Game> , Game> getTable(){
        if (table == null){
            table = new ObjectMap<>();
        }
        return table;
    }

    /** 注册应用；同类型重建时替换旧实例，并同步指向旧实例的当前应用引用。 */
    public static void register(Game game){
        Objects.requireNonNull(game, "Game must not be null");
        Game previous = getTable().put(game.getClass(),game);
        if (previous != null && current == previous) current = game;
    }
    /** 设置当前应用并使按类型查询返回同一实例。 */
    public static void setCurrent(Game game){
        register(game);
        current = game;
    }
    /** 按实例注销；旧应用迟到的释放不能移除同类型的新应用，也不负责释放应用资源。 */
    public static void unregister(Game game) {
        if (game == null) return;
        if (table != null && table.get(game.getClass()) == game) table.remove(game.getClass());
        if (current == game) current = null;
    }
    /** 返回当前应用；尚未设置时记录错误并返回空值。 */
    public static Game getCurrent(){
        if (current == null && Gdx.app != null) {
            Gdx.app.error(TAG,"Failed to get current game,Current game game is null,Please first use 'setCurrent' to set the current game");
        }
        return current;
    }
    /** 按具体类型查询当前注册实例，不保留已注销的宿主引用。 */
    public static Game getGame(Class<? extends Game> zClass){
        ObjectMap<Class<? extends Game>, Game> table = getTable();
        if (!table.containsKey(zClass)) {
            if (Gdx.app != null) Gdx.app.error(TAG,"This Game is not register in GameManager: "+zClass.getSimpleName());
            return null;
        }
        return table.get(zClass);
    }
}
