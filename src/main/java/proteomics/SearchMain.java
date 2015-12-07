package proteomics;

import proteomics.Parameter.Parameter;
import proteomics.Search.FinalResultEntry;
import proteomics.Search.PrepareSearch;
import proteomics.Search.Search;
import proteomics.Validation.CalFDR;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SearchMain {

    public static void main(String[] args) throws Exception {
        // Get current time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss Z");
        Calendar cal_start = Calendar.getInstance();
        Date date_start = cal_start.getTime();
        float time_start = System.nanoTime();

        // Process inputs
        if (args.length != 2) {
            help();
        }

        // Set parameters
        String parameter_path = args[0].trim();
        String msxml_path = args[1].trim();

        // Get the parameter map
        Parameter parameter = new Parameter(parameter_path);
        Map<String, String> parameter_map = parameter.returnParameterMap();

        LogEntry log_entry = new LogEntry("");

        // Prepare search
        System.out.println("Indexing database...");
        log_entry.output_str += "Indexing database...";
        PrepareSearch ps = new PrepareSearch(parameter_map);

        // Searching...
        Search search = new Search(ps, log_entry, parameter_map);
        List<FinalResultEntry> search_results = search.doSearch(msxml_path);

        if (search_results.isEmpty()) {
            System.out.println("There is no PSM.");
            log_entry.output_str += "There is no PSM.";
        } else {
            // save result
            System.out.println("Estimating q value...");
            log_entry.output_str += "Estimating q value...";
            List<List<FinalResultEntry>> picked_result = pickResult(search_results);
            CalFDR cal_fdr_obj = new CalFDR(picked_result.get(0), false);
            List<FinalResultEntry> intra_result = cal_fdr_obj.includeStats();
            Collections.sort(intra_result, Collections.<FinalResultEntry>reverseOrder());
            cal_fdr_obj = new CalFDR(picked_result.get(1), false);
            List<FinalResultEntry> inter_result = cal_fdr_obj.includeStats();
            Collections.sort(inter_result, Collections.<FinalResultEntry>reverseOrder());
            System.out.println("Saving results...");
            log_entry.output_str += "Saving results...";
            saveResult(intra_result, inter_result, msxml_path);
        }

        // Get end time
        Calendar cal_end = Calendar.getInstance();
        Date date_end = cal_end.getTime();
        float time_end = System.nanoTime();

        double duration = (time_end - time_start) * 1e-9;

        try (BufferedWriter target_writer = new BufferedWriter(new FileWriter(msxml_path + ".log.txt"))) {
            target_writer.write(msxml_path + " finished.\r\n"
                    + "Started on: " + sdf.format(date_start) + "\r\n"
                    + "Ended on: " + sdf.format(date_end) + "\r\n"
                    + "Duration: " + (int) duration + " second" + "\r\n"
                    + "\r\n"
                    + "Log: \r\n"
                    + log_entry.output_str + "\r\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.out.println("Done.");
    }

    private static void saveResult(List<FinalResultEntry> intra_result, List<FinalResultEntry> inter_result, String id_file_name) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(id_file_name + ".intra.csv"))) {
            writer.write("scan_num,spectrum_precursor_mz,charge,score,delta_score,abs_ppm,peptide_1,site_1,mod_1,protein_1,peptide_2,site_2,mod_2,protein_2,q_value\n");
            for (FinalResultEntry re : intra_result) {
                if (re.type.contentEquals("11")) {
                    int link_site_1 = re.link_site_1 + 1;
                    int link_site_2 = re.link_site_2 + 1;
                    writer.write(re.spectrum_id + "," + re.spectrum_precursor_mz + "," + re.charge + "," + String.format("%.4f", re.score) + "," + String.format("%.2f", re.delta_score) + "," + String.format("%.2f", re.abs_ppm) + "," + re.seq_1 + "," + link_site_1 + "," + re.mod_1 + "," + re.pro_id_1 + "," + re.seq_2 + "," + link_site_2 + "," + re.mod_2 + "," + re.pro_id_2 + "," + String.format("%.2f", re.qvalue) + "\n");
                }
            }
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
            System.exit(1);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(id_file_name + ".inter.csv"))) {
            writer.write("scan_num,spectrum_precursor_mz,charge,score,delta_score,abs_ppm,peptide_1,site_1,mod_1,protein_1,peptide_2,site_2,mod_2,protein_2,q_value\n");
            for (FinalResultEntry re : inter_result) {
                if (re.type.contentEquals("11")) {
                    int link_site_1 = re.link_site_1 + 1;
                    int link_site_2 = re.link_site_2 + 1;
                    writer.write(re.spectrum_id + "," + re.spectrum_precursor_mz + "," + re.charge + "," + String.format("%.4f", re.score) + "," + String.format("%.2f", re.delta_score) + "," + String.format("%.2f", re.abs_ppm) + "," + re.seq_1 + "," + link_site_1 + "," + re.mod_1 + "," + re.pro_id_1 + "," + re.seq_2 + "," + link_site_2 + "," + re.mod_2 + "," + re.pro_id_2 + "," + String.format("%.2f", re.qvalue) + "\n");
                }
            }
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static List<List<FinalResultEntry>> pickResult(List<FinalResultEntry> search_result) {
        List<List<FinalResultEntry>> picked_result = new LinkedList<>();
        List<FinalResultEntry> inter_protein_result = new LinkedList<>();
        List<FinalResultEntry> intra_protein_result = new LinkedList<>();

        for (FinalResultEntry result_entry : search_result) {
            if (result_entry.cl_type.contentEquals("intra_protein")) {
                intra_protein_result.add(result_entry);
            } else {
                inter_protein_result.add(result_entry);
            }
        }

        picked_result.add(intra_protein_result);
        picked_result.add(inter_protein_result);

        return picked_result;
    }

    private static void help() {
        String help_str = "ECL version 20151127\r\n"
                + "A cross-linked peptides identification tool.\r\n"
                + "Author: Fengchao Yu\r\n"
                + "Email: fyuab@connect.ust.hk\r\n"
                + "ECL usage: java -Xmx32g -jar /path/to/ECL.jar <parameter_file> <data_file>\r\n"
                + "\t<parameter_file>: parameter file. Can be download along with ECL.\r\n"
                + "\t<data_file>: spectra data file (mzXML)\r\n"
                + "\texample: java -Xmx32g -jar ECL.jar parameter.def data.mzxml";
        System.out.print(help_str);
        System.exit(1);
    }
}
