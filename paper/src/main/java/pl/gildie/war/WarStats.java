package pl.gildie.war;

public class WarStats {
    private int kills;
    private int deaths;
    private int rankingGained;
    private int koxyEaten;    // enchanted golden apple
    private int refillsUsed;  // zwykle golden apple + potiony + golden carrot
    private int pearlsUsed;
    private int tntFired;
    private int eggHits;

    public WarStats() { }

    public WarStats(int kills, int deaths, int rankingGained, int koxyEaten,
                    int refillsUsed, int pearlsUsed, int tntFired, int eggHits) {
        this.kills = kills;
        this.deaths = deaths;
        this.rankingGained = rankingGained;
        this.koxyEaten = koxyEaten;
        this.refillsUsed = refillsUsed;
        this.pearlsUsed = pearlsUsed;
        this.tntFired = tntFired;
        this.eggHits = eggHits;
    }

    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public void setKills(int v) { kills = v; }
    public int getDeaths() { return deaths; }
    public void addDeath() { deaths++; }
    public void setDeaths(int v) { deaths = v; }
    public int getRankingGained() { return rankingGained; }
    public void setRankingGained(int v) { rankingGained = v; }
    public int getKoxyEaten() { return koxyEaten; }
    public void addKox() { koxyEaten++; }
    public void setKoxyEaten(int v) { koxyEaten = v; }
    public int getRefillsUsed() { return refillsUsed; }
    public void addRefill() { refillsUsed++; }
    public void setRefillsUsed(int v) { refillsUsed = v; }
    public int getPearlsUsed() { return pearlsUsed; }
    public void addPearl() { pearlsUsed++; }
    public void setPearlsUsed(int v) { pearlsUsed = v; }
    public int getTntFired() { return tntFired; }
    public void addTnt() { tntFired++; }
    public void setTntFired(int v) { tntFired = v; }
    public int getEggHits() { return eggHits; }
    public void addEggHit() { eggHits++; }
    public void setEggHits(int v) { eggHits = v; }

    public void reset() {
        kills = 0; deaths = 0; rankingGained = 0; koxyEaten = 0;
        refillsUsed = 0; pearlsUsed = 0; tntFired = 0; eggHits = 0;
    }
}
