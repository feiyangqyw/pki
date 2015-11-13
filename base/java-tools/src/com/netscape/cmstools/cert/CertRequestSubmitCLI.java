package com.netscape.cmstools.cert;

import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;

import com.netscape.certsrv.ca.AuthorityID;
import com.netscape.certsrv.cert.CertEnrollmentRequest;
import com.netscape.certsrv.cert.CertRequestInfos;
import com.netscape.certsrv.profile.ProfileAttribute;
import com.netscape.certsrv.profile.ProfileInput;
import com.netscape.cmstools.cli.CLI;
import com.netscape.cmstools.cli.MainCLI;

import netscape.ldap.util.DN;
import netscape.ldap.util.RDN;
import netscape.security.x509.X500Name;

public class CertRequestSubmitCLI extends CLI {

    CertCLI certCLI;

    public CertRequestSubmitCLI(CertCLI certCLI) {
        super("request-submit", "Submit certificate request", certCLI);
        this.certCLI = certCLI;

        Option option = new Option(null, "issuer-id", true, "Authority ID (host authority if omitted)");
        option.setArgName("ID");
        options.addOption(option);

        option = new Option(null, "issuer-dn", true, "Authority DN (host authority if omitted)");
        option.setArgName("DN");
        options.addOption(option);

        option = new Option(null, "username", true, "Username for request authentication");
        option.setArgName("username");
        options.addOption(option);

        option = new Option(null, "password", false, "Prompt password for request authentication");
        options.addOption(option);

        option = new Option(null, "profile", true, "Certificate profile");
        option.setArgName("profile");
        options.addOption(option);

        option = new Option(null, "request-type", true, "Request type (default: pkcs10)");
        option.setArgName("type");
        options.addOption(option);

        option = new Option(null, "csr-file", true, "File containing the CSR");
        option.setArgName("path");
        options.addOption(option);

        option = new Option(null, "subject", true, "Subject DN");
        option.setArgName("DN");
        options.addOption(option);
    }

    public void printHelp() {
        formatter.printHelp(getFullName() + " <filename> [OPTIONS...]", options);
    }

    @Override
    public void execute(String[] args) throws Exception {
        // Always check for "--help" prior to parsing
        if (Arrays.asList(args).contains("--help")) {
            // Display usage
            printHelp();
            System.exit(0);
        }

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            printHelp();
            System.exit(-1);
        }

        String[] cmdArgs = cmd.getArgs();

        String requestFilename = cmdArgs.length > 0 ? cmdArgs[0] : null;
        String profileID = cmd.getOptionValue("profile");

        if (requestFilename == null && profileID == null) {
            System.err.println("Error: Missing request file or profile ID.");
            printHelp();
            System.exit(-1);
        }

        if (requestFilename != null && profileID != null) {
            System.err.println("Error: Request file and profile ID are mutually exclusive.");
            printHelp();
            System.exit(-1);
        }

        AuthorityID aid = null;
        if (cmd.hasOption("issuer-id")) {
            String aidString = cmd.getOptionValue("issuer-id");
            try {
                aid = new AuthorityID(aidString);
            } catch (IllegalArgumentException e) {
                System.err.println("Bad AuthorityID: " + aidString);
                printHelp();
                System.exit(-1);
            }
        }

        X500Name adn = null;
        if (cmd.hasOption("issuer-dn")) {
            String adnString = cmd.getOptionValue("issuer-dn");
            try {
                adn = new X500Name(adnString);
            } catch (IOException e) {
                System.err.println("Bad DN: " + adnString);
                printHelp();
                System.exit(-1);
            }
        }

        if (aid != null && adn != null) {
            System.err.println("--issuer-id and --issuer-dn options are mutually exclusive");
            printHelp();
            System.exit(-1);
        }

        String requestType = cmd.getOptionValue("request-type");

        CertEnrollmentRequest request;
        if (requestFilename == null) { // if no request file specified, generate new request from profile

            if (verbose) {
                System.out.println("Retrieving " + profileID + " profile.");
            }

            request = certCLI.certClient.getEnrollmentTemplate(profileID);

            // set default request type for new request
            if (requestType == null) requestType = "pkcs10";

        } else { // otherwise, load request from file

            if (verbose) {
                System.out.println("Loading request from " + requestFilename + ".");
            }

            String xml = loadFile(requestFilename);
            request = CertEnrollmentRequest.fromXML(xml);
        }

        if (requestType != null) {

            if (verbose) {
                System.out.println("Request type: " + requestType);
            }

            for (ProfileInput input : request.getInputs()) {
                ProfileAttribute typeAttr = input.getAttribute("cert_request_type");
                if (typeAttr != null) {
                    typeAttr.setValue(requestType);
                }
            }
        }

        String csrFilename = cmd.getOptionValue("csr-file");
        if (csrFilename != null) {

            String csr = loadFile(csrFilename);

            if (verbose) {
                System.out.println("CSR:");
                System.out.println(csr);
            }

            for (ProfileInput input : request.getInputs()) {
                ProfileAttribute csrAttr = input.getAttribute("cert_request");
                if (csrAttr != null) {
                    csrAttr.setValue(csr);
                }
            }
        }

        String subjectDN = cmd.getOptionValue("subject");
        if (subjectDN != null) {
            DN dn = new DN(subjectDN);
            Vector<?> rdns = dn.getRDNs();

            Map<String, String> subjectAttributes = new HashMap<String, String>();
            for (int i=0; i< rdns.size(); i++) {
                RDN rdn = (RDN)rdns.elementAt(i);
                String type = rdn.getTypes()[0].toLowerCase();
                String value = rdn.getValues()[0];
                subjectAttributes.put(type, value);
            }

            ProfileInput sn = request.getInput("Subject Name");
            if (sn != null) {
                if (verbose) System.out.println("Subject Name:");

                for (ProfileAttribute attribute : sn.getAttributes()) {
                    String name = attribute.getName();
                    String value = null;

                    if (name.equals("subject")) {
                        // get the whole subject DN
                        value = subjectDN;

                    } else if (name.startsWith("sn_")) {
                        // get value from subject DN
                        value = subjectAttributes.get(name.substring(3));

                    } else {
                        // unknown attribute, ignore
                        if (verbose) System.out.println(" - " + name);
                        continue;
                    }

                    if (value == null) continue;

                    if (verbose) System.out.println(" - " + name + ": " + value);
                    attribute.setValue(value);
                }
            }
        }

        String certRequestUsername = cmd.getOptionValue("username");
        if (certRequestUsername != null) {
            request.setAttribute("uid", certRequestUsername);
        }

        if (cmd.hasOption("password")) {
            Console console = System.console();
            String certRequestPassword = new String(console.readPassword("Password: "));
            request.setAttribute("pwd", certRequestPassword);
        }

        CertRequestInfos cri = certCLI.certClient.enrollRequest(request, aid, adn);
        MainCLI.printMessage("Submitted certificate request");
        CertCLI.printCertRequestInfos(cri);
    }

    private String loadFile(String fileName) throws FileNotFoundException {
        try (Scanner scanner = new Scanner(new File(fileName))) {
            return scanner.useDelimiter("\\A").next();
        }
    }
}
