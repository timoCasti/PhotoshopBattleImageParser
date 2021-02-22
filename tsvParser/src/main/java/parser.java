import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import org.nibor.autolink.*;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public class parser {

    /**
     * takes two System Arguments:
     *  1. the file to be processsed in json format, starting with "RC" for commeents and "RS" for submissions
     *  2. the path to the geckodriver (https://github.com/mozilla/geckodriver/)
     */

    public static void main(String[] args) throws Exception {

        String geckodriverPath = args[1];
        System.out.println(geckodriverPath);
        System.setProperty("webdriver.gecko.driver", geckodriverPath);
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(true);

        File f = new File(args[0]);
        String commentOrSubmission = args[0];
        System.out.println(args[0]);

        WebDriver driver = new FirefoxDriver(options);
        // RS => Submissions  RC=> Comments
        try {
            if (commentOrSubmission.contains("RC")) {
                parseComments(f, commentOrSubmission, driver);
            } else if (commentOrSubmission.contains("RS")) {
                parseSubmissions(f, commentOrSubmission, driver);
            } else {
                System.out.println("Not Desired File Name");
            }
        } finally {
            driver.quit();
        }
    }

    /**
     * tries to get the Format through the ending of an url
     *
     * @param url an url to an image
     * @return
     */
    private static String getFormat(String url) {
        String[] split = url.split("\\.");
        return split[split.length - 1];
    }

    /**
     * sets up the Jsoup connection
     *
     * @param url the url to connect to
     * @return
     */
    private static Connection getConnection(String url) {
        return Jsoup.connect(url).followRedirects(true).timeout(10_000).maxBodySize(40_000_000).ignoreContentType(true);
    }

    /**
     * mehod which deletes the ending of an URL/image
     *
     * @param withEnding the url with the ending to be removed
     * @return
     */
    private static String deleteEnding(String withEnding) {
        try {
            String[] split = withEnding.split("\\.");
            int cutSize = withEnding.length() - split[split.length - 1].length();
            return withEnding.substring(0, cutSize - 1);
        }
        catch (Exception e){
            return withEnding;
        }
        }



    /**
     * Writes parsed file to tsv and writes a txt file with the statistics
     *
     * @param outputlist list of parsed obejects which will be written into the tsv file
     * @param path       where the tsv file will be written and named
     * @param stats      list with statistical and debug information
     * @param <T>        either a original_out for submissions or an photoshop_out for comments
     * @throws IOException
     */
    private static <T> void finalizeOutput(List<T> outputlist, String path, List<String> stats) throws IOException {

        //write outputlist to .tsv file
        CsvMapper mapperCSV = new CsvMapper();
        mapperCSV.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

        if (outputlist.size() == 0) {
            return;
        }
        if (outputlist.get(0) instanceof photoshop_out) {
            CsvSchema schema = mapperCSV.schemaFor(photoshop_out.class).withHeader();
            schema = schema.withColumnSeparator('\t').withoutQuoteChar();
            ObjectWriter myObjectWriter = mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(), outputlist);
        } else {
            CsvSchema schema = mapperCSV.schemaFor(original_out.class).withHeader();
            schema = schema.withColumnSeparator('\t').withoutQuoteChar();
            ObjectWriter myObjectWriter = mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(), outputlist);
        }
        //write file with unsupported urls
        FileWriter writer = new FileWriter(deleteEnding(path) + "stats.txt");
        for (String str : stats) {
            writer.write(str + System.lineSeparator());
        }
        writer.close();
    }

    /**
     * trims img url remove everything after question mark
     *
     * @param url url to be trimmed
     * @return trimmed url
     */
    private static String trimUrl(String url) {

        int index = url.indexOf('?');
        if (index != -1) {
            url = url.substring(0, index);
        }
        return url;
    }

    /**
     * Method which parses the comment files (starting with "RC")
     *
     * @param file                the input file with the comments to parse
     * @param commentOrSubmission the name of the file
     * @param driver              the driver of the headless browser
     * @throws IOException
     * @throws InterruptedException
     */
    private static void parseComments(File file, String commentOrSubmission, WebDriver driver) throws IOException, InterruptedException {
        String path = deleteEnding(commentOrSubmission) + ".tsv";
        List<photoshop_out> outList = new ArrayList<>();
        List<String> statisticAndDebug = new ArrayList<>();
        int counter_toplvl = 0;
        int counter_score = 0;
        int counter_imgSize = 0;
        int counter_multipleImages = 0;
        int counter_comments = 0;
        int counter_success = 0;
        List<String> bad_body = new ArrayList<>();
        HashMap<Integer, Integer> scoreDistribution = new HashMap<>();

        // Read json file to list of comment objects
        ObjectMapper mapper = new ObjectMapper();
        comment[] commentList = mapper.readValue(file, comment[].class);

        for (comment comment : commentList) {
            counter_comments++;
            // first check if it is a top level comment by looking at the prefix of parent_id="t3"_id
            // also look at the score to get rid of spam => score >= 20
            String parent_id = comment.getParent_id();
            String prefix = parent_id.substring(0, 2);

            if (!prefix.equals("t3")) {
                counter_toplvl++;
                continue;
            }

            // get latest score and the title from reddit
            String linkToScore = "https://reddit.com/r/photoshopbattles/comments/" + comment.getParent_id().substring(3) + "/a/" + comment.getId() + ".json?depth=1";
            String score = null;
            String title = "";

            try {
                String js = Jsoup.connect(linkToScore).ignoreContentType(true).execute().body();
                title = js.substring((js.indexOf("\"title\"") + 8));
                title = title.substring(title.indexOf("\"") + 1);
                title = title.substring(0, title.indexOf("\""));
                if (title.contains("PsBattle:")) {
                    title = title.substring(10);
                }
                if (title.contains("\\")) {
                    title = title.substring(title.indexOf("\\"));
                }

                score = js.substring(js.lastIndexOf("\"score\":") + 9);
                score = score.substring(0, score.indexOf(","));
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (score == null) {
                score = comment.getScore();
            }
            int sc = Integer.parseInt(score);

            // get ScoreDistribution for debugging
            if (scoreDistribution.containsKey(sc)) {
                int oldValue = scoreDistribution.get(sc);
                scoreDistribution.put(sc, oldValue + 1);
            } else {
                scoreDistribution.put(sc, 1);
            }

            // filter comments with score lower than 20
            if (sc < 20) {
                counter_score++;
                continue;
            }

            //check if it is an empty/deleted comment
            String bo = comment.getBody();
            if (bo.contains("[removed]") || bo.contains("[deleted]")) {
                continue;
            }

            // fix the body string to be readable by the url detector
            int index;
           // bo= bo.replace("]"," ");
            bo= bo.replace("["," ");
            bo= bo.replace("("," ");
          //  bo= bo.replace(")"," ");


            //https://github.com/linkedin/URL-Detector
            //UrlDetector parser = new UrlDetector(bo, UrlDetectorOptions.Default);
            //List<Url> urlList = parser.detect();

            List<String> urlString=new ArrayList<>();
            String placeHolder;

            //cast url to String and get rid of duplicates in the list
            /*
            for (int i=0; i<urlList.size();i++){
                placeHolder=urlList.get(i).toString();
                urlString.add(placeHolder);
            }
            */

            // https://github.com/robinst/autolink-java
            String linkFromlinkExtractor;
            LinkExtractor linkExtractor = LinkExtractor.builder()
                    .linkTypes(EnumSet.of(LinkType.URL, LinkType.WWW))
                    .build();
            try {
                Iterable<LinkSpan> links = linkExtractor.extractLinks(bo);
                LinkSpan linkSpan = links.iterator().next();
                linkSpan.getType();        // LinkType.URL
                linkSpan.getBeginIndex();  // 17
                linkSpan.getEndIndex();    // 32
                linkFromlinkExtractor = bo.substring(linkSpan.getBeginIndex(), linkSpan.getEndIndex());  // "http://test.com"
                urlString.add(linkFromlinkExtractor);
            } catch (Exception e) {
                e.printStackTrace();
            }


            Set<String> set = new LinkedHashSet<>();
            set.addAll(urlString);
            urlString.clear();
            urlString.addAll(set);



            if (!(urlString.size() == 1)) { // we only consider comments with only one link
                bad_body.add(bo+" number of urls: " + urlString.size());
                counter_multipleImages++;
                continue;
            }
            BufferedImage bimg = null;
            int imgCounterLink = 0;

            boolean processedLink = false; //check if a link provides at least one image if not write to unsupported file

            //fix the url's
            //String link = String.valueOf(urlList.get(0));
            String link= urlString.get(0);
            while (link.startsWith("(") || link.startsWith("[") || link.startsWith(".")) {
                link = link.substring(1);
            }
            while (link.endsWith(")") || link.endsWith("]") || link.endsWith(".")) {
                link = link.substring(0, link.length() - 1);
            }

            String linkBuilder = "reddit.com/r/photoshopbattles/comments/" + comment.getParent_id().substring(3) + "/" + title + "/" + comment.getId();

            //retrieve the images with headless browser
            try {
                driver.get(link);
                Thread.sleep(1500); // easiest way to wait till any page is loaded
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements img = doc.getElementsByTag("img");
            String imgDuplicate = "";
            for (Element element : img) {
                String src = element.absUrl("src");
                if (src.toLowerCase().contains("i.imgur") || src.toLowerCase().contains("pinimg.com") || src.toLowerCase().contains("pbs.twigmg.com") || src.toLowerCase().contains("upload.wikimedia.org") || src.toLowerCase().contains("ytimg.com") || src.toLowerCase().contains("i.reddituploads.com") || src.toLowerCase().contains("puu.sh") || src.toLowerCase().contains("flickr.com") || src.toLowerCase().contains("deviantart.com") || src.toLowerCase().contains("en.wikipedia.org") || src.toLowerCase().contains("i.redd.it")) { //

                    //check for img duplicates, since images on e.g. imgur are present multiple times with different endings
                    if (imgDuplicate.equals(deleteEnding(src))) {
                        continue;
                    }
                    imgDuplicate = deleteEnding(src);

                    URL url = null;
                    try {
                        url = new URL(src);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (url == null) {
                        continue;
                    }

                    try {
                        bimg = ImageIO.read(url);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (bimg == null) {
                        continue;
                    }

                    byte[] imgByte = null;
                    try {
                        imgByte = getConnection(src).execute().bodyAsBytes();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                    String format = "";
                    //try to get the format/ending of an image
                    try {
                        format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                        if (format.startsWith("image/")) {
                            format = format.substring(6);
                        }
                    } catch (Exception e) {
                        format = getFormat(src);
                    }
                    // to avoid formats which can cause errors
                    if (!format.equals("jpeg") && !format.equals("png") && !format.equals("jpg")) {
                        format = "jpeg";
                    }

                    //check if images are big enough
                    if (imgByte.length < 10000 || bimg.getWidth() < 300 || bimg.getHeight() < 300) { //check for min size of 10kB for and image and is at least 300x300
                        counter_imgSize++;
                        continue;
                    }
                    String idSuffix = String.valueOf(imgCounterLink);
                    imgCounterLink++;

                    //trim img url remove everything after question mark
                    src = trimUrl(src);

                    //create photoshop out which is the content of the output file
                    photoshop_out po = new photoshop_out(comment.getId() + "_" + idSuffix, parent_id.substring(3), src, format, sha256Hex(imgByte), String.valueOf(imgByte.length), comment.getScore(), comment.getAuthor(), linkBuilder, comment.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                    outList.add(po);
                    processedLink = true;
                    counter_success++;
                }
            }
            if (!processedLink) { //saves links which do not provide an image to an additional file
                statisticAndDebug.add(link);
            }
        }

        // treemap to get ordrerd map
        TreeMap<Integer, Integer> tm = new TreeMap<>(scoreDistribution);
        File scoreDis = new File("scoreDis");
        BufferedWriter bf = null;

        // write score distribution
        try {
            //create new BufferedWriter for the output file
            bf = new BufferedWriter(new FileWriter(scoreDis));

            //iterate map entries
            for (Map.Entry<Integer, Integer> entry : tm.entrySet()) {
                //put key and value separated by a colon
                bf.write(entry.getKey() + ":" + entry.getValue());
                bf.newLine();
            }
            bf.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                //always close the writer
                bf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FileWriter writer = new FileWriter(deleteEnding(path) + "_body.txt");
        for (String str : bad_body) {
            writer.write(str + System.lineSeparator());
        }
        writer.close();

        //Debug file
        statisticAndDebug.add(0, "Number of comments successful parsed:  " + counter_success);
        statisticAndDebug.add(1, "Number of comments parsed:  " + counter_comments);
        statisticAndDebug.add(2, "Number of comments which are not top lvl:   " + counter_toplvl);
        statisticAndDebug.add(3, "Number of comments with score below 20  :  " + counter_score);
        statisticAndDebug.add(4, "Number of comments with more than one image link:  " + counter_multipleImages);
        statisticAndDebug.add(5, "Number of comments with too small images:  " + counter_imgSize);

        finalizeOutput(outList, path, statisticAndDebug);
    }

    /**
     * Method which parses the submissions/posts (starting with "RS") to tsv
     *
     * @param f                   the file to parse
     * @param commentOrSubmission file name as string
     * @param driver              driver of the headless browser
     * @throws Exception
     */
    private static void parseSubmissions(File f, String commentOrSubmission, WebDriver driver) throws Exception {
        //submissions file

        String path = deleteEnding(commentOrSubmission) + ".tsv";
        List<original_out> outList = new ArrayList<>();
        List<String> statisticsAndDebug = new ArrayList<>();

        int counter_score = 0;
        int counter_imgSize = 0;
        int counter_submisssions = 0;
        int counter_success = 0;
        // Read json file to list
        ObjectMapper mapper = new ObjectMapper();
        submission[] submissionList = mapper.readValue(f, submission[].class);
        int emptyURL = 0;


        BufferedImage bimg = null;
        for (submission sub : submissionList) {
            counter_submisssions++;
            boolean processedSubmission = false;

            //create permalink referring to the post
            String linkBuilder = "reddit.com/r/photoshopbattles/comments/" + sub.getId();

            String linkToScore = "https://reddit.com/r/photoshopbattles/comments/" + sub.getId() + ".json?depth=1";
            String score = null;

            try {
                String js = Jsoup.connect(linkToScore).ignoreContentType(true).execute().body();
                score = js.substring(js.indexOf("\"score\":") + 9);
                score = score.substring(0, score.indexOf(","));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (score == null) {
                score = sub.getScore();
            }
            int sc = Integer.parseInt(score);

            String sUrl = sub.getUrl();
            sUrl = trimUrl(sUrl);


            if (sc < 20) {  //score of post needs to be at least 20, to filter spam
                counter_score++;
                continue;
            }
            /*
            String ending = getFormat(sUrl);
            boolean isImg = ending.contains("png") || ending.contains("jpg") || ending.contains("jpeg");
            if (isImg) {

                URL url = null;
                try {
                    url = new URL(sUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (url == null) {
                    System.out.println("no url");
                    continue;
                }
                try {
                    bimg = ImageIO.read(url);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //it might be https instead of http
                if (bimg == null && sUrl.startsWith("http:")) {
                    String nUrl = "https" + sUrl.substring(4);
                    url = new URL(nUrl);
                    try {
                        bimg = ImageIO.read(url);

                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                }
                if (bimg == null) {
                    System.out.println("failed to open url:  " + sUrl);
                    continue;
                }

                byte[] imgByte = null;
                try {
                    imgByte = getConnection(sUrl).execute().bodyAsBytes();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                String format = "";
                try {
                    format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                    if (format.startsWith("image/")) {
                        format = format.substring(6);
                    }
                } catch (Exception e) {
                    format = getFormat(sUrl);
                    e.printStackTrace();
                }
                // to avoid formats which can cause errors
                if (!format.equals("jpeg") && !format.equals("png") && !format.equals("jpg")) {
                    format = "jpeg";
                }

                if (imgByte.length < 10000 || bimg.getHeight() < 300 || bimg.getWidth() < 300) {  //check for min size of 10kB for and image
                    counter_imgSize++;
                    continue;
                }


                original_out po = new original_out(sub.getId(), sUrl, format, sha256Hex(imgByte), String.valueOf(imgByte.length), sub.getScore(), sub.getAuthor(), linkBuilder, sub.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                outList.add(po);
                processedSubmission = true;
                counter_success++;


            } //url does not directly refer to an image checkout provided url for images with headless Browser
            else */
            if (!sUrl.equals("")) {

                //try {
                try {
                    driver.get(sUrl);
                } catch (Exception e) {
                    continue;
                }
                Thread.sleep(1500); // easiest way to wait till any page is loaded

                Document doc;
                try {
                    doc = Jsoup.parse(driver.getPageSource());

                } catch (Exception e) {
                    continue;
                }
                if (doc == null) {
                    continue;
                }
                Elements img = doc.getElementsByTag("img");

                String imgDuplicate = "";
                for (Element element : img) {
                    String src = element.absUrl("src");

                    // are not actual post with images but anouncements with examples
                        if(src.contains("preview.redd")){
                            continue;
                        }

                        //check for img duplicates, since images on e.g. imgur are present multiple times with different endings
                        if (imgDuplicate.equals(deleteEnding(src))) {
                            continue;
                        }
                        imgDuplicate = deleteEnding(src);
                        URL url;
                        try {
                            url = new URL(src);
                        } catch (Exception e) {
                            continue;
                        }
                        try {
                            bimg = ImageIO.read(url);
                        } catch (Exception e) {
                            continue;
                        }
                        if (bimg == null) {
                            continue;
                        }
                        byte[] imgByte = null;
                        try {
                            imgByte = getConnection(sUrl).execute().bodyAsBytes();
                        } catch (Exception e) {
                            continue;
                        }
                        src = trimUrl(src);
                        String format = "";
                        try {
                            format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                            if (format.startsWith("image/")) {
                                format = format.substring(6);
                            }
                        } catch (Exception e) {
                            format = getFormat(src);

                        }
                        // to avoid formats which can cause errors
                        if (!format.equals("jpeg") && !format.equals("png") && !format.equals("jpg")) {
                            format = "jpeg";
                        }

                        if (imgByte.length < 10000 || bimg.getHeight() < 300 || bimg.getWidth() < 300) { //check for min size of 10kB for and image
                            counter_imgSize++;
                            continue;
                        }
                        //trim img url remove everything after question mark


                        //create original_out which holds the content of the output file
                        original_out po = new original_out(sub.getId(), src, format, sha256Hex(imgByte), String.valueOf(imgByte.length), sub.getScore(), sub.getAuthor(), linkBuilder, sub.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                        outList.add(po);
                        processedSubmission = true;
                        counter_success++;

                }

            } else {//url is empty
                emptyURL++;
            }
            if (!processedSubmission) {
                statisticsAndDebug.add(sUrl);
            }
        }
        statisticsAndDebug.add("Number of empty url:  " + emptyURL);
        statisticsAndDebug.add(0, "Number of submissions successful parsed:  " + counter_success);
        statisticsAndDebug.add(1, "Number of submissions parsed:  " + counter_submisssions);
        statisticsAndDebug.add(2, "Number of submissions with score below 20:  " + counter_score);
        statisticsAndDebug.add(3, "Number of submissions with too small images:  " + counter_imgSize);

        finalizeOutput(outList, path, statisticsAndDebug);

    }
}

