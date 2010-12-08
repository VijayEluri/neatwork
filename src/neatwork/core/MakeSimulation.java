package neatwork.core;

import neatwork.*;

import neatwork.core.defs.*;

import neatwork.core.run.*;

import neatwork.solver.*;

import neatwork.utils.*;

import java.util.*;


/**
 * Classe qui execute la simulation
 * <p>
 * voici les param\u00E8tres du make design : <br>
 * - <i>typesimu</i> = type de simulation (="random" ou "tapbytap" ou "handmade").<br>
 * - <i>typeorifice</i> = type d'orifice utilis\u00E9 (="ideal" ou "commercial").<br>
 * - <i>nbsimu</i> = nombre de simulation.<br>
 * - <i>minoutflow</i> = flot minimum en sortie<br>
 * - <i>simopentaps</i> = fraction de robinets ouverts durant la simu <br>
 * - <i>mincriticalflow</i> = flot minimal critique (pour les r\u00E9sultats)<br>
 * - <i>maxcriticalflow</i> = flot maximal critique (pour les r\u00E9sultats)<br>
 * - <i>alpha</i> = coefficient de calcul (a ne pas redefinir) <br>
 * - <i>coefforifice</i> = coefficient des orifices <br>
 * @author L. DROUET
 * @version 1.0
 */
public class MakeSimulation {
    private CoreDesign dsg;
    private DiametersVector dvector;
    private Properties properties;
    private double rate;
    private int nbsim;
    private String typeSimu;

    public MakeSimulation(CoreDesign design, DiametersVector dvector,
        OrificesVector ovector, Properties prop, Hashtable faucetRef,
        AbstractSolver solver) {
        this.dsg = design;
        this.properties = prop;

        //extraction des properties
        String typeOrifice = prop.getProperty("simu.typeorifice.value", "ideal"); //$NON-NLS-1$ //$NON-NLS-2$
        String typeSimulation = prop.getProperty("simu.typesimu.value", "random"); //$NON-NLS-1$ //$NON-NLS-2$
        this.typeSimu = typeSimulation;

        int nbSim = Integer.parseInt(prop.getProperty("simu.nbsim.value", "10")); //$NON-NLS-1$ //$NON-NLS-2$
        this.nbsim = nbSim;

        double outflow = Double.parseDouble(prop.getProperty(
                    "topo.targetflow.value", "0.2")) / 1000; //$NON-NLS-1$ //$NON-NLS-2$
        double alpha = Double.parseDouble(prop.getProperty(
                    "topo.faucetcoef.value", "0.00000002")); //$NON-NLS-1$ //$NON-NLS-2$
        double rate1 = 1;

        try {
            rate1 = Double.parseDouble(prop.getProperty(
                        "simu.simopentaps.value", "0.4")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (NumberFormatException ex) {
        }

        this.rate = rate1;

        double seuil = Double.parseDouble(prop.getProperty(
                    "simu.mincriticalflow.value", "0.1")); //$NON-NLS-1$ //$NON-NLS-2$
        double seuil2 = Double.parseDouble(prop.getProperty(
                    "simu.maxcriticalflow.value", "0.3")); //$NON-NLS-1$ //$NON-NLS-2$
        double coeffOrifice = Double.parseDouble(prop.getProperty(
                    "topo.orifcoef.value", "0.59")); //$NON-NLS-1$ //$NON-NLS-2$

        // ajout de de noeuds intermediaires pour chaque branche possedant deux tuyaux differents
        ajoutNodes();

        // si le reseau contient des boucles, on ajoute des branches inverses
        if (!isTree()) {
            addInversBranch();
        }

        //type d'orifice
        if (typeOrifice.equals("ideal")) { //$NON-NLS-1$

            for (int i = 0; i < dsg.tvector.size(); i++) {
                Taps taps = (Taps) dsg.tvector.get(i);
                taps.orifice = taps.orif_ideal;
            }
        } else {
            for (int i = 0; i < dsg.tvector.size(); i++) {
                Taps taps = (Taps) dsg.tvector.get(i);
                taps.orifice = taps.orif_com;
            }
        }

        //variable de taille
        int n = ((dvector.size() * dsg.pvector.size()) + dsg.nvector.size()) -
            1;
        int m = dsg.nvector.size() + dsg.pvector.size();

        // Initialise le vecteur de flot
        double[] F = new double[dsg.tvector.size() + (dsg.pvector.size() * 2) +
            1];

        //affectation des alpha
        for (int k = 0; k < dsg.tvector.size(); k++) {
            ((Taps) dsg.tvector.get(k)).faucetCoef = alpha;
        }

        //simulation au hasard
        if (typeSimulation.equals("random")) { //$NON-NLS-1$

            // stat \u00E0 zero
            dsg.pvector.initializeSimulation(nbSim);
            dsg.nvector.initializeSimulation(nbSim);

            int i;

            //resolution
            for (i = 0; i < nbSim; i++) {
                solver.setProgress(Math.round((float) i / nbSim * 100));

                RunSimulation simulation = new RunSimulation(F, dsg.nvector,
                        dsg.pvector, dsg.tvector, outflow, rate1, seuil, seuil2,
                        typeSimulation, i, coeffOrifice);
            }
        }

        // simulation robinet par robinet
        if (typeSimulation.equals("tapbytap")) { //$NON-NLS-1$

            //Remet les stats des precedentes simulation \uFFFD 0
            dsg.pvector.initializeSimulation(dsg.tvector.size());
            dsg.nvector.initializeSimulation(dsg.tvector.size());

            //On lance NbSim simulation a la resolution du solveur
            RunSimulation simulation = new RunSimulation(F, dsg.nvector,
                    dsg.pvector, dsg.tvector, outflow, rate1, seuil, seuil2,
                    typeSimulation, dsg.tvector.size(), coeffOrifice, solver);
        }

        // simulation handmade
        if (typeSimulation.equals("handmade")) { //$NON-NLS-1$

            Enumeration enun = dsg.tvector.elements();

            while (enun.hasMoreElements()) {
                Taps tap = (Taps) enun.nextElement();
                tap.select = "close"; //$NON-NLS-1$

                if (faucetRef.get(tap.taps) != null) {
                    if (faucetRef.get(tap.taps).equals(new Boolean(true))) {
                        tap.select = "open"; //$NON-NLS-1$
                    }
                }
            }

            dsg.pvector.initializeSimulation(1);
            dsg.nvector.initializeSimulation(1);
            solver.setProgress(50);

            RunSimulation simulation = new RunSimulation(F, dsg.nvector,
                    dsg.pvector, dsg.tvector, outflow, rate1, seuil, seuil2,
                    typeSimulation, 0, coeffOrifice);
        }

        //Calcul les vitesses dans chaque tuyau
        dsg.pvector.CalculSpeed();

        //quartiles
        calculQuartile();

        //stats sur les pressions
        calculStatPressure();
    }

    /** Ajoute des noeuds interm\u00E9diaires pour les tuyaux coup\u00E9s en 2*/
    private void ajoutNodes() {
        Pipes pipes;
        Pipes pip;
        Nodes nodes;
        Nodes n1;
        Nodes n2;
        int i = 0;

        while (i < dsg.pvector.size()) {
            pipes = (Pipes) dsg.pvector.elementAt(i);

            //
            if (pipes.l2 != 0) {
                n1 = (Nodes) dsg.nvector.elementAt(dsg.nvector.getPosition(
                            pipes.nodes_beg));
                n2 = (Nodes) dsg.nvector.elementAt(dsg.nvector.getPosition(
                            pipes.nodes_end));
                pip = new Pipes(pipes.nodes_beg + "*" + pipes.nodes_end, //$NON-NLS-1$
                        pipes.nodes_end,
                        pipes.nodes_beg + "*" + pipes.nodes_end, 0); //$NON-NLS-1$
                pipes.nodes_end = pipes.nodes_beg + "*" + pipes.nodes_end; //$NON-NLS-1$
                pip.l1 = pipes.l2;
                pip.d1 = pipes.d2;
                pip.p1 = pipes.p2;
                pip.q1 = pipes.q2;
                pip.beta1 = pipes.beta2;

                nodes = new Nodes(pipes.nodes_end, 0, 0);
                nodes.ajout = 1;
                nodes.height = n1.height +
                    ((n2.height - n1.height) / pipes.length * pipes.l1);

                if (i >= (dsg.pvector.size() - 1 - dsg.tvector.size())) {
                    i++;
                    dsg.nvector.add(dsg.nvector.size() - dsg.tvector.size(),
                        nodes);
                    dsg.pvector.add(dsg.pvector.size() - dsg.tvector.size(), pip);
                } else {
                    dsg.nvector.add(i + 1, nodes);
                    dsg.pvector.add(i + 1, pip);
                }
            }

            i++;
        }
    }

    public String getPropertiesContent() {
        String content = ""; //$NON-NLS-1$

        //ajoute les properties ( 3 champs)
        content += Messages.getString("MakeSimulation.Default_properties"); //$NON-NLS-1$

        //content += "!Name-Value\n";
        Enumeration iter = properties.propertyNames();

        while (iter.hasMoreElements()) {
            String name = iter.nextElement().toString();

            if (name.startsWith("simu.") && name.endsWith(".value")) { //$NON-NLS-1$ //$NON-NLS-2$
                content += (name.substring(5, name.length() - 6) + "," + //$NON-NLS-1$
                properties.getProperty(name) + ",N\n"); //$NON-NLS-1$
            }
        }

        return content;
    }

    /** renvoie les flots de sortie pour chaque robinets
     *  <p>
     *  Le format de sortie est le suivant: tap ID, moyenne de flots, min, max
     *  et d\u00E9tail des simulations.
     */
    public Vector getResultsSimu() {
        Vector v = new Vector();

        for (int i = dsg.pvector.size() - dsg.tvector.size();
                i < dsg.pvector.size(); i++) {
            Pipes pipes = (Pipes) dsg.pvector.get(i);

            if (dsg.nvector.getNbTaps(pipes.nodes_end) == 0) {
                String name = pipes.nodes_end;

                if (name.lastIndexOf("*") > 0) { //$NON-NLS-1$
                    name = name.substring(name.lastIndexOf("*") + 1, //$NON-NLS-1$
                            name.length());
                }

                if (name.lastIndexOf("_") > 0) { //$NON-NLS-1$

                    int n = name.substring(name.lastIndexOf("_") + 1, //$NON-NLS-1$
                            name.length()).charAt(0) - 'a' + 1;
                    name = name.substring(0, name.lastIndexOf("_") + 1) + n; //$NON-NLS-1$
                }

                Vector line = new Vector();
                line.add(name);
                line.add(Tools.doubleFormat("0.####", pipes.min)); //$NON-NLS-1$
                line.add(Tools.doubleFormat("0.####", pipes.moyenne)); //$NON-NLS-1$
                line.add(Tools.doubleFormat("0.####", pipes.max)); //$NON-NLS-1$

                for (int j = 0; j < pipes.simulation.length; j++) {
                    line.add(Tools.doubleFormat("0.####", pipes.simulation[j])); //$NON-NLS-1$
                }

                v.add(line);
            }
        }

        return v;
    }

    /** renvoie les flots de sortie pour chaque robinets
     *  <p>
     *  Le format de sortie est le suivant: tap ID, moyenne de flots, min, max
     *  et d\u00E9tail des simulations.
     */
    public Vector getSimpleResultsSimu() {
        Vector v = new Vector();
        int totsim = 0;
        int tots1 = 0;
        int tots2 = 0;
        int totfail = 0;
        double totmin = 500000000;
        double totmax = -1;
        double totmoy = 0;

        //robinets
        for (int i = dsg.pvector.size() - dsg.tvector.size();
                i < dsg.pvector.size(); i++) {
            Pipes pipes = (Pipes) dsg.pvector.get(i);
            Vector line = new Vector();

            String name = pipes.nodes_end;

            if (name.lastIndexOf("*") > 0) { //$NON-NLS-1$
                name = name.substring(name.lastIndexOf("*") + 1, name.length()); //$NON-NLS-1$
            }

            if (name.lastIndexOf("_") > 0) { //$NON-NLS-1$

                int n = name.substring(name.lastIndexOf("_") + 1, name.length()) //$NON-NLS-1$
                    .charAt(0) - 'a' + 1;
                name = name.substring(0, name.lastIndexOf("_") + 1) + n; //$NON-NLS-1$
            }

            line.add(name);
            line.add("" + pipes.nbsim); //$NON-NLS-1$
            totsim += pipes.nbsim;
            line.add(Tools.doubleFormat("0.####", pipes.min)); //$NON-NLS-1$

            line.add(Tools.doubleFormat("0.####", pipes.moyenne)); //$NON-NLS-1$
            totmoy += (pipes.moyenne * pipes.nbsim);
            line.add(Tools.doubleFormat("0.####", pipes.max)); //$NON-NLS-1$

            double variability = 0;

            try {
                variability = Math.sqrt(pipes.moyennec -
                        (pipes.moyenne * pipes.moyenne)) / pipes.moyenne * 100;
            } catch (Exception ex) {
            }

            line.add(Tools.doubleFormat("0.##", variability)); //$NON-NLS-1$
            line.add("" + //$NON-NLS-1$
                Tools.doubleFormat("0.##", //$NON-NLS-1$
                    ((double) pipes.seuil) / pipes.nbsim * 100));
            tots1 += pipes.seuil;
            line.add("" + //$NON-NLS-1$
                Tools.doubleFormat("0.##", //$NON-NLS-1$
                    ((double) pipes.seuil2) / pipes.nbsim * 100));
            tots2 += pipes.seuil2;
            line.add("" + pipes.failure); //$NON-NLS-1$
            totfail += pipes.failure;
            line.add("-1"); //$NON-NLS-1$
            v.add(line);
        }

        Vector line = new Vector();
        line.add(Messages.getString("MakeSimulation.Global_average")); //$NON-NLS-1$
        line.add("-"); //$NON-NLS-1$
        line.add("-"); //$NON-NLS-1$
        line.add(Tools.doubleFormat("0.####", totmoy / totsim)); //$NON-NLS-1$
        line.add("-"); //$NON-NLS-1$
        line.add("?"); //$NON-NLS-1$
        line.add("" + //$NON-NLS-1$
            Tools.doubleFormat("0.##", ((double) tots1) / totsim * 100)); //$NON-NLS-1$
        line.add("" + //$NON-NLS-1$
            Tools.doubleFormat("0.##", ((double) tots2) / totsim * 100)); //$NON-NLS-1$
        line.add("" + totfail); //$NON-NLS-1$
        line.add("-1"); //$NON-NLS-1$
        v.insertElementAt(line, 0);

        return v;
    }

    /** renvoie les statistiques de pression
     *  <p>
     *  Le format de sortie est le suivant: node ID, average pressure,
     *  minimum pressure, maximum pressure
     */
    public Vector getPressureSimu() {
        Vector v = new Vector();

        for (int i = 0; i < dsg.nvector.size(); i++) {
            Nodes nodes = (Nodes) dsg.nvector.get(i);
            Vector line = new Vector();
            line.add(nodes.nodes);
            line.add(Tools.doubleFormat("0.##", nodes.minpress)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.##", nodes.averpress)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.##", nodes.maxpress)); //$NON-NLS-1$
            v.add(line);
        }

        return v;
    }

    /** renvoie les statistiques de vitesse de l'eau dans les tuyaux
     *  <p>
     *  Le format de sortie est le suivant:
     */
    public Vector getSpeedSimu() {
        Vector v = new Vector();

        for (int i = 0; i < dsg.pvector.size(); i++) {
            Pipes pipes = (Pipes) dsg.pvector.get(i);
            Vector line = new Vector();
            String name = pipes.nodes_end;

            if (name.lastIndexOf("_") > 0) { //$NON-NLS-1$

                int n = name.substring(name.lastIndexOf("_") + 1, name.length()) //$NON-NLS-1$
                    .charAt(0) - 'a' + 1;
                name = name.substring(0, name.lastIndexOf("_") + 1) + n; //$NON-NLS-1$
            }

            line.add(pipes.nodes_beg + " -> " + name); //$NON-NLS-1$
            line.add("" + pipes.nbsim); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.##", pipes.speed)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.##", pipes.speedmax)); //$NON-NLS-1$
            line.add("-1"); //$NON-NLS-1$
            v.add(line);
        }

        return v;
    }

    /** renvoie les statistiques de vitesse de l'eau
     *  <p>
     *  Le format de sortie est le suivant:
     */
    public Vector getQuartileSimu() {
        Vector v = new Vector();

        for (int i = dsg.pvector.size() - dsg.tvector.size();
                i < dsg.pvector.size(); i++) {
            Pipes pipes = (Pipes) dsg.pvector.get(i);
            Vector line = new Vector();
            String name = pipes.nodes_end;

            if (name.lastIndexOf("*") > 0) { //$NON-NLS-1$
                name = name.substring(name.lastIndexOf("*") + 1, name.length()); //$NON-NLS-1$
            }

            if (name.lastIndexOf("_") > 0) { //$NON-NLS-1$

                int n = name.substring(name.lastIndexOf("_") + 1, name.length()) //$NON-NLS-1$
                    .charAt(0) - 'a' + 1;
                name = name.substring(0, name.lastIndexOf("_") + 1) + n; //$NON-NLS-1$
            }

            line.add(name);
            line.add("" + pipes.nbsim); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.min)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.quart10)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.quart25)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.quart50)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.quart75)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.quart90)); //$NON-NLS-1$
            line.add(Tools.doubleFormat("0.####", pipes.max)); //$NON-NLS-1$
            line.add("N"); //$NON-NLS-1$
            line.add("N"); //$NON-NLS-1$
            v.add(line);
        }

        return v;
    }

    /** calcul les differents quartiles sur les simulations*/
    private void calculQuartile() {
        for (int k = dsg.pvector.size() - dsg.tvector.size();
                k < dsg.pvector.size(); k++) {
            Pipes pipes = (Pipes) dsg.pvector.get(k);

            //copie du tableau de simu
            double[] simu = new double[pipes.simulation.length];

            for (int i = 0; i < pipes.simulation.length; i++) {
                simu[i] = pipes.simulation[i];
            }

            //tri
            boolean bool = false;

            while (bool == false) {
                bool = true;

                for (int i = 0; i < (simu.length - 1); i++) {
                    if (((simu[i] > simu[i + 1]) && (simu[i + 1] != 0)) ||
                            ((simu[i] < simu[i + 1]) && (simu[i] == 0))) {
                    //if (simu[i] > simu[i + 1]) {
                        bool = false;

                        double tamp = simu[i];
                        simu[i] = simu[i + 1];
                        simu[i + 1] = tamp;
                    }
                }
            }
            
            //puts failures first
            for (int i = simu.length - 1; i > pipes.failure - 1; i--) {
                simu[i] = simu[i - pipes.failure];
            }
            for (int i = 0; i < (pipes.failure - 1); i++) {
            	simu[i] = 0;
            }
            
            //affectation quartile
            pipes.quart10 = simu[(int) Math.floor(pipes.nbsim * 0.1)];
            pipes.quart25 = simu[(int) Math.floor(pipes.nbsim * 0.25)];
            pipes.quart50 = simu[(int) Math.floor(pipes.nbsim * 0.5)];
            pipes.quart75 = simu[(int) Math.floor(pipes.nbsim * 0.75)];
            pipes.quart90 = simu[(int) Math.floor(pipes.nbsim * 0.9)];

            //affectation effectif
            double ecart = pipes.max - pipes.min;
            pipes.quarteff10 = 0;
            pipes.quarteff25 = 0;
            pipes.quarteff50 = 0;
            pipes.quarteff75 = 0;
            pipes.quarteff100 = pipes.nbsim;

            for (int i = 0; i < simu.length; i++) {
                if (simu[i] <= ((ecart * 0.1) + pipes.min)) {
                    pipes.quarteff10++;
                }

                if (simu[i] <= ((ecart * 0.25) + pipes.min)) {
                    pipes.quarteff25++;
                }

                if (simu[i] <= ((ecart * 0.50) + pipes.min)) {
                    pipes.quarteff50++;
                }

                if (simu[i] <= ((ecart * 0.75) + pipes.min)) {
                    pipes.quarteff75++;
                }

                if (simu[i] <= ((ecart * 0.90) + pipes.min)) {
                    pipes.quarteff90++;
                }
            }

            pipes.quarteff100 -= pipes.quart90;
            pipes.quarteff100 *= (100 / 10);
            pipes.quarteff90 -= pipes.quart75;
            pipes.quarteff90 *= (100 / 15);
            pipes.quarteff75 -= pipes.quart50;
            pipes.quarteff75 *= (100 / 25);
            pipes.quarteff50 -= pipes.quart25;
            pipes.quarteff50 *= (100 / 25);
            pipes.quarteff25 -= pipes.quart10;
            pipes.quarteff50 *= (100 / 15);
            pipes.quarteff10 *= (100 / 10);
        }
    }

    /** calcul les pressions observ\u00E9es*/
    private void calculStatPressure() {
        for (int i = 1; i < dsg.nvector.size(); i++) {
            Nodes nodes = (Nodes) dsg.nvector.get(i);
            nodes.minpress = 10000;
            nodes.maxpress = nodes.pressim[0];

            for (int j = 0; j < nodes.pressim.length; j++) {
                if (nodes.pressim[j] == 0) {
                    nodes.pressim[j] = -nodes.height;
                }

                /* moyenne */
                nodes.averpress = nodes.averpress + nodes.pressim[j];

                /* min */
                if (nodes.pressim[j] < nodes.minpress) {
                    nodes.minpress = nodes.pressim[j];
                }

                /* max */
                if (nodes.pressim[j] > nodes.maxpress) {
                    nodes.maxpress = nodes.pressim[j];
                }
            }

            nodes.averpress = nodes.averpress / nodes.pressim.length;
        }
    }

    /**
     * Cette procedure verifie que le reseau est un arbre ou non.
     * Si un noeud a un nombre de predecesseurs sup a 1
     * alors le reseau n'est pas un arbre
     */
    private boolean isTree() {
        boolean tree = true;

        /*Nodes nodes;
        for(int i = 0 ; i < dsg.nvector.size() ; i++){
          nodes = (Nodes) dsg.nvector.elementAt(i);
          if(dsg.pvector.GetNumberOfPred(nodes) > 1){
            Tree = false;
            i = dsg.nvector.size();
          }
        }*/
        Enumeration e = dsg.nvector.elements();

        while ((tree) && (e.hasMoreElements())) {
            Nodes n = (Nodes) e.nextElement();

            if (dsg.pvector.GetNumberOfPred(n) > 1) {
                tree = false;
            }
        }

        return tree;
    }

    /**
     * Ajoute pour chaque branche de l'arbre une branche inverse
     * si elle n'existe pas deja
     */
    private void addInversBranch() {
        Pipes pipes; /* Pipes trait\uFFFD */
        Pipes invpipes; /* Pipes invers\uFFFD */
        Nodes source = (Nodes) dsg.nvector.elementAt(0); /* on prend la source */

        for (int i = 0; i < dsg.pvector.size(); i++) {
            pipes = (Pipes) dsg.pvector.elementAt(i);

            /* si la branche inverse n'exste pas deja (a faire)*/
            /* ne pas effectuer l'op\uFFFDration sur les robinets et sur la source */
            if ((dsg.nvector.getNbTaps(pipes.nodes_end) == 0) &&
                    (dsg.tvector.isTaps(pipes.nodes_end) == false) &&
                    (!pipes.nodes_beg.equalsIgnoreCase(source.nodes))) {
                invpipes = new Pipes(pipes.nodes_end, pipes.nodes_beg,
                        pipes.nodes_end, 0 /*pipes.length*/);
                invpipes.l1 = pipes.l1;
                invpipes.l2 = pipes.l2;
                invpipes.d1 = pipes.d1;
                invpipes.d2 = pipes.d2;
                invpipes.beta1 = pipes.beta1;
                invpipes.beta2 = pipes.beta2;
                invpipes.p1 = pipes.p1;
                invpipes.p2 = pipes.p2;
                invpipes.q1 = pipes.q1;
                invpipes.q2 = pipes.q2;

                //dsg.pvector.add(i+1,invpipes);
                dsg.pvector.insertElementAt(invpipes, 1);
                i++;
            }
        }
    }
}
