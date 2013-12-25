package energyPLAN.optimization.step1.jmetal.problems;

import jmetal.core.Problem;
import jmetal.core.Solution;
import jmetal.encodings.variable.ArrayReal;
import jmetal.encodings.solutionType.ArrayRealSolutionType;
import jmetal.encodings.solutionType.BinaryRealSolutionType;
import jmetal.encodings.solutionType.RealSolutionType;
import jmetal.util.JMException;
import jmetal.util.wrapper.XReal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;

import energyPLAN.file.parse.*;

public class EnergyPLANProblemStep1 extends Problem {

	MultiMap energyplanmMap;

	/**
	 * Creates a new instance of problem ZDT1.
	 * 
	 * @param numberOfVariables
	 *            Number of variables.
	 * @param solutionType
	 *            The solution type must "Real", "BinaryReal, and "ArrayReal".
	 */
	public EnergyPLANProblemStep1(String solutionType) {
		numberOfVariables_ = 7;
		numberOfObjectives_ = 2;
		numberOfConstraints_ = 1;
		problemName_ = "EneryPLANProblemStep1";

		upperLimit_ = new double[numberOfVariables_];
		lowerLimit_ = new double[numberOfVariables_];

		// Establishes upper and lower limits for the variables
		int var;

		for (var = 0; var < 4; var++) {
			// capccities of wind, off-shore wind, PV and condencing power unit
			lowerLimit_[var] = 0.0;
			upperLimit_[var] = 10000.0;
		} // for

		for (; var < numberOfVariables_; var++) {
			// share of coal, oil and natural-gas
			lowerLimit_[var] = 0.0;
			upperLimit_[var] = 1.0;

		}

		if (solutionType.compareTo("ArrayReal") == 0)
			solutionType_ = new ArrayRealSolutionType(this);
		else {
			System.out.println("Error: solution type " + solutionType
					+ " invalid");
			System.exit(-1);
		}
	} // constructor end

	/**
	 * Evaluates a solution.
	 * 
	 * @param solution
	 *            The solution to evaluate.
	 * @throws JMException
	 */
	public void evaluate(Solution solution) throws JMException {

		writeModificationFile(solution);
		String energyPLANrunCommand = ".\\EnergyPLAN_SEP_2013\\EnergyPLAN.exe -i \".\\EnergyPLAN_SEP_2013\\energyPlan data\\Data\\RefModelForOptimization.txt\" -m \"modification.txt\" -ascii \"result.txt\" ";
		try {
			// Process process = new
			// ProcessBuilder(energyPLANrunCommand).start();
			Process process = Runtime.getRuntime().exec(energyPLANrunCommand);
			process.waitFor();
			process.destroy();
			EnergyPLANFileParse epfp = new EnergyPLANFileParse(".\\result.txt");
			energyplanmMap = epfp.parseFile();

			Iterator it;
			Collection<String> col;

			col = (Collection<String>) energyplanmMap
					.get("CO2-emission (total)");
			it = col.iterator();
			solution.setObjective(0, Double.parseDouble(it.next().toString()));
			col = (Collection<String>) energyplanmMap.get("TOTAL ANNUAL COSTS");
			it = col.iterator();
			solution.setObjective(1, Double.parseDouble(it.next().toString()));
				
			// check warning
			col = (Collection<String>) energyplanmMap.get("WARNING");
			if (col != null) 
			{
			/*	System.out.println("No warning");
			} 
			else {*/
				@SuppressWarnings("rawtypes")
				Iterator it3 = col.iterator();
				if (!it3.next().toString()
						.equals("PP too small. Critical import is needed"))
						throw new IOException("warning!!"+it3.next().toString());
					//System.out.println("Warning " + it3.next().toString());
			}

		} catch (IOException e) {
			System.out.println("Energyplan.exe has some problem");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println("Energyplan interrupted");
		}
	}

	@SuppressWarnings("unchecked")
	public void evaluateConstraints(Solution solution) throws JMException {
		Iterator it;
		Collection<String> col;

		col = (Collection<String>) energyplanmMap.get("Maximumimport");
		it = col.iterator();
		int maximumImport = Integer.parseInt(it.next().toString());

		double constrints = 2500 - maximumImport;
		if (constrints < 0.0) {
			solution.setOverallConstraintViolation(constrints);
			solution.setNumberOfViolatedConstraint(1);

		} else {

			solution.setOverallConstraintViolation(0.0);
			solution.setNumberOfViolatedConstraint(0);
		}

	}

	void writeModificationFile(Solution solution) {

		DecimalFormat twoDForm = new DecimalFormat("0.00");
		
		
		// wind
		double RE1 = ((ArrayReal) solution.getDecisionVariables()[0]).array_[0];
		// off-shore wind
		double RE2 = ((ArrayReal) solution.getDecisionVariables()[0]).array_[1];
		// PV
		double RE3 = ((ArrayReal) solution.getDecisionVariables()[0]).array_[2];
		// condencing PP
		double PP = ((ArrayReal) solution.getDecisionVariables()[0]).array_[3];

		// PP coal share
		double PP_coal_share = ((ArrayReal) solution.getDecisionVariables()[0]).array_[4];
		// pp oil sahre
		double PP_oil_share = ((ArrayReal) solution.getDecisionVariables()[0]).array_[5];
		// pp Ngas share
		double PP_ngas_share = ((ArrayReal) solution.getDecisionVariables()[0]).array_[6];

		final double PP_coal_eff=0.3;
		final double PP_oil_eff=0.4;
		final double PP_ngas_eff=0.5;
		
		//normalized the share
		double nor_PP_coal_share = PP_coal_share / (PP_coal_share+PP_oil_share+PP_ngas_share);
		double nor_PP_oil_share = PP_oil_share / (PP_coal_share+PP_oil_share+PP_ngas_share);
		double nor_PP_ngas_share = PP_ngas_share / (PP_coal_share+PP_oil_share+PP_ngas_share);
		
		
		double overall_eff = ((PP*nor_PP_coal_share)*PP_coal_eff + (PP*nor_PP_oil_share)*PP_oil_eff + (PP*nor_PP_ngas_share)*PP_ngas_eff)/PP;
		
		
		
		try {

			File file = new File("modification.txt");
			if (file.exists()) {
				file.delete();

			}

			file.createNewFile();

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			String str = "EnergyPLAN version";
			bw.write(str);
			bw.newLine();
			str = "698";
			bw.write(str);
			bw.newLine();

			str = "input_RES1_capacity=";
			bw.write(str);
			bw.newLine();
			str = "" + (int) RE1;
			bw.write(str);
			bw.newLine();

			str = "input_RES2_capacity=";
			bw.write(str);
			bw.newLine();
			str = "" + (int) RE2;
			bw.write(str);
			bw.newLine();

			str = "input_RES3_capacity=";
			bw.write(str);
			bw.newLine();
			str = "" + (int) RE3;
			bw.write(str);
			bw.newLine();

			str = "input_cap_pp_el=";
			bw.write(str);
			bw.newLine();
			str = "" + (int) PP;
			bw.write(str);
			bw.newLine();

			str = "input_fuel_PP[1]=";
			bw.write(str);
			bw.newLine();
			str = "" + twoDForm.format(PP_coal_share);
			bw.write(str);
			bw.newLine();

			str = "input_fuel_PP[2]=";
			bw.write(str);
			bw.newLine();
			str = "" + twoDForm.format(PP_oil_share);
			bw.write(str);
			bw.newLine();

			str = "input_fuel_PP[3]=";
			bw.write(str);
			bw.newLine();
			str = "" + twoDForm.format(PP_ngas_share);
			bw.write(str);
			bw.newLine();

			str = "input_eff_pp_el=";
			bw.write(str);
			bw.newLine();
			str = "" + twoDForm.format(overall_eff);
			bw.write(str);
			bw.newLine();

			bw.close();
			// file.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
