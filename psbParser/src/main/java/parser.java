import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

public class parser {

    public static void main(String[] args) throws Exception {

        comment c;
        submission s;
        File f = new File(args[0]);
        int counterPSB = 0;
        int counterOut = 0;

        String commentOrSubmission = args[0];
        // RS => Submissions  RC=> Comments
        if (commentOrSubmission.contains("RS")) {
            //submiision
            MappingIterator<submission> iterator;
            iterator = new ObjectMapper().readerFor(submission.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValues(f);
            List<submission> submissionList = new ArrayList<submission>();

            List<submission> invalidSubmission = new ArrayList<>();

            System.out.println(f.toString());

            while (iterator.hasNextValue()) {
                s = iterator.nextValue();
                //check if subbreddit_id equals the subreddit id of PSbattle
                if (s.getSubreddit_id() != null) {
                    //System.out.println("no subreddit_id");
                    if (s.getSubreddit_id().equals("t5_2tecy")) {
                        submissionList.add(s);
                        counterPSB++;
                    }
                } else if (s.getSubreddit() != null) {
                    if (s.getSubreddit().equals("photoshopbattles")) {
                        submissionList.add(s);
                        counterPSB++;
                    }
                } else {
                    System.out.println("subreddit not defined");
                    invalidSubmission.add(s);
                    counterOut++;
                }


            }
            System.out.println(submissionList.size() + "    size list");
            String path = args[0] + "_psb.json";
            String parh2 = args[0] + "_inval";
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectMapper objectMapper2 = new ObjectMapper();
            try {
                objectMapper.writeValue(Paths.get(path).toFile(), submissionList);
                objectMapper2.writeValue(Paths.get(parh2).toFile(), invalidSubmission);

            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("number of submission elements written:  " + submissionList.size());
            System.out.println("number of sorted out elements:  " + counterOut);


        } else {
            MappingIterator<comment> iterator;
            iterator = new ObjectMapper().readerFor(comment.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValues(f);
            List<comment> commentList = new ArrayList<comment>();
            System.out.println(f.toString());
            try {
                while (iterator.hasNextValue()) {

                    c = iterator.nextValue();


                    //check if subbreddit_id equals the subreddit id of PSbattle
                    if (c.getSubreddit_id().equals("t5_2tecy")) {
                        commentList.add(c);
                        counterPSB++;
                    } else if (c.getSubreddit().equals("photoshopbattles")) {
                        commentList.add(c);
                        counterPSB++;

                    } else {
                        counterOut++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println(commentList.size() + "    size list");
            String path = args[0] + "_psb.json";

            int size = commentList.size();

            ObjectMapper objectMapper = new ObjectMapper();
            try {
                objectMapper.writeValue(Paths.get(path).toFile(), commentList);

            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("number of elements written:  " + commentList.size());
            System.out.println("number of sorted out elements:  " + counterOut);


        }
    }

}

