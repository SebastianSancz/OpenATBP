package xyz.openatbp.extension.game.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.MapData;
import xyz.openatbp.extension.game.ActorType;

import java.awt.geom.Point2D;

public class Base extends Actor {
    private boolean unlocked = false;
    private long lastHit = -1;

    public Base(ATBPExtension parentExt, Room room, int team){
        this.currentHealth = 3500;
        this.maxHealth = 3500;
        this.team = team;
        if(team == 0){
            id = "base_purple";
            location = new Point2D.Float(MapData.L2_BASE1_X*-1,0f);
        }
        else{
            id = "base_blue";
            location = new Point2D.Float(MapData.L2_BASE1_X,0f);
        }
        this.parentExt = parentExt;
        this.avatar = id;
        this.actorType = ActorType.BASE;
        this.room = room;
    }

    public int getTeam(){
        return this.team;
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if(!this.unlocked) return false;
        this.currentHealth-=damage;
        if(System.currentTimeMillis() - this.lastHit >= 15000){
            this.lastHit = System.currentTimeMillis();
            for(UserActor ua : parentExt.getRoomHandler(room.getId()).getPlayers()){
                if(ua.getTeam() == this.team) ExtensionCommands.playSound(parentExt, ua.getUser(), "global", "announcer/base_under_attack",new Point2D.Float(0,0));
            }
        }
        int oppositeTeam = 0;
        if(this.team == 0) oppositeTeam = 1;
        if(this.currentHealth <= 0) this.parentExt.getRoomHandler(this.room.getId()).gameOver(oppositeTeam);

        if(this.currentHealth <= 0){
            try{
                parentExt.getRoomHandler(this.room.getId()).gameOver(oppositeTeam);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) currentHealth);
        updateData.putDouble("pHealth", getPHealth());
        updateData.putInt("maxHealth", (int) maxHealth);
        ExtensionCommands.updateActorData(parentExt,this.room,updateData);
        return !(currentHealth > 0);
    }

    @Override
    public void attack(Actor a) {
        System.out.println("Bases can't attack silly!");
    }

    @Override
    public void die(Actor a) {

    }

    @Override
    public void update(int msRan) {

    }

    @Override
    public void setTarget(Actor a) {

    }

    public void unlock(){
        unlocked = true;
    }

    public boolean isUnlocked(){
        return this.unlocked;
    }
}
