/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package mapred.map;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.util.ReflectionUtils;

import app.PrefixTree;

/**
 * Gerar itemsets de tamanho k, k+1 e k+3 em uma única instância Map/Reduce.
 * @author eduardo
 */
public class Map3  extends Mapper<LongWritable, Text, Text, IntWritable>{
    
    Log log = LogFactory.getLog(Map3.class);
    IntWritable countOut = new IntWritable(1);
    SequenceFile.Reader reader;
    ArrayList<String> fileCached;
    ArrayList<String> itemsetAux;
    PrefixTree prefixTree;
    int k;
    int mink, maxk = 0;
    /**
     * Le o arquivo invertido para a memória.
     * @param context
     * @throws IOException 
     */
    @Override
    public void setup(Context context) throws IOException{
        String count = context.getConfiguration().get("count");
        String fileCachedRead = context.getConfiguration().get("fileCachedRead");
        String kStr = context.getConfiguration().get("k");
        k = Integer.parseInt(kStr);
        double earlierTime = Double.parseDouble(context.getConfiguration().get("earlierTime"));
        
        
        log.info("Iniciando map 3 count = "+count);
        log.info("Arquivo Cached = "+fileCachedRead);
        
        log.info("Tempo da fase anterior é "+earlierTime);
        
        URI[] patternsFiles = context.getCacheFiles();
        
        Path path = new Path(patternsFiles[0].toString());
        
        reader = new SequenceFile.Reader(context.getConfiguration(), SequenceFile.Reader.file(path));
        openFile(fileCachedRead, context);
        
        //Gerar combinações dos itens de acordo com o tamanho de lk e do tempo gasto da fase anterior
        
        prefixTree = new PrefixTree(0);
        itemsetAux = new ArrayList<String>();
        
        log.info("K is "+k);
        mink = k;
        
        String itemsetC;
        prefixTree.printStrArray(fileCached);
        
        if(fileCachedRead != null && fileCached.size() > 0){
        	if(fileCached.get(fileCached.size()-1).split(" ").length < k-1){
	        	log.info("Itemsets é menor do que k");
	        	prefixTree.printStrArray(fileCached);
	        	System.exit(0);
        	}
        }else{
        	log.info("Arquivo do cache distribuído é vazio!");
        	return;
        }
        
        int lkSize = fileCached.size();
        
        int ct;
        
        if(earlierTime >= 60){
        	ct = lkSize * 1;
        }else{
        	ct = (int)Math.round(lkSize * 1.2);
        }
        String[] itemA;
        String[] itemB;
        
        log.info("O valor de ct é "+ct);
        
        for (int i = 0; i < fileCached.size(); i++){
        	for (int j = i+1; j < fileCached.size(); j++){
        		itemA = fileCached.get(i).split(" ");
        		itemB = fileCached.get(j).split(" ");
        		if(isSamePrefix(itemA, itemB, i, j)){
        			itemsetC = combine(itemA, itemB);
        			itemsetAux.add(itemsetC);
        			//System.out.println(itemsetC+" no primeiro passo");
        			//Building HashTree
        			prefixTree.add(prefixTree, itemsetC.split(" "), 0);
        		}
        	}
        }
        
        log.info("Inicia o loop dinâmico");
        
        int cSetSize = itemsetAux.size();
        while( cSetSize <= ct){
        	//System.out.println("Cset size "+cSetSize);
        	fileCached.clear();
        	if(itemsetAux.isEmpty()){
        		k--;
        		break;
        	}
        	
        	k++;
	        for (int i = 0; i < itemsetAux.size(); i++){
	        	for (int j = i+1; j < itemsetAux.size(); j++){
	        		itemA = itemsetAux.get(i).split(" ");
	        		itemB = itemsetAux.get(j).split(" ");
	        		if(isSamePrefix(itemA, itemB, i, j)){
	        			itemsetC = combine(itemA, itemB);
	        			fileCached.add(itemsetC);
	        			//System.out.println(itemsetC+" no primeiro passo");
	        			//Building HashTree
	        			prefixTree.add(prefixTree, itemsetC.split(" "), 0);
	        		}
	        	}
	        }
	        if(fileCached.isEmpty()){
	        	k--;
        		break;
        	}
	        k++;
	        itemsetAux.clear();
	        for (int i = 0; i < fileCached.size(); i++){
	        	for (int j = i+1; j < fileCached.size(); j++){
	        		itemA = fileCached.get(i).split(" ");
	        		itemB = fileCached.get(j).split(" ");
	        		if(isSamePrefix(itemA, itemB, i, j)){
	        			itemsetC = combine(itemA, itemB);
	        			itemsetAux.add(itemsetC);
	        			//System.out.println(itemsetC+" no segundp passo");
	        			//Building HashTree
	        			prefixTree.add(prefixTree, itemsetC.split(" "), 0);
	        		}
	        	}
	        }
	        cSetSize += itemsetAux.size();
        }
       
        prefixTree.printStrArray(itemsetAux);
        prefixTree.printPrefixTree(prefixTree);
        //System.out.println("Fim do setup, inicia função map para o k = "+k);
        maxk = k;
        System.out.println("MinK "+mink+" maxK "+maxk);
    }
    
    /**
     * 
     * @param itemA
     * @param itemB
     * @param i
     * @param j
     * @return
     */
    public boolean isSamePrefix(String[] itemA, String[] itemB, int i, int j){
    	if(k == 2) return true;
    	for(int a = 0; a < k -2; a++){
            if(!itemA[a].equals(itemB[a])){
            	//System.out.println("Não é o mesmo prefixo: "+itemA[a]+" != "+itemB[a]);
                return false;
            }
        }
        
    	return true;
    }
    
    /**
     * 
     * @param itemA
     * @param itemB
     * @return
     */
    public String combine(String[] itemA, String[] itemB){
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < itemA.length; i++){
            sb.append(itemA[i]).append(" ");
        }
        sb.append(itemB[itemB.length-1]);
        return sb.toString();
    }
    
    /**
     * 
     * @param transaction
     * @param pt
     * @param i
     * @param itemset
     * @param itemsetIndex
     * @param context
     */
    private void subSet(String[] transaction, PrefixTree pt, int i, String[] itemset, int itemsetIndex, Context context) {
    	
    	if(i >= transaction.length){
			return;
		}

		if(pt.getLevel() > maxk){
			return;
		}

		int index =  pt.getPrefix().indexOf(transaction[i]);
		
		if(index == -1){
			return;
		}else{
			itemset[itemsetIndex] = transaction[i];
			i++;
			if(pt.getLevel() >= mink-1){
				StringBuilder sb = new StringBuilder();

				for(String s: itemset){
					if(s != null && !s.isEmpty())
						sb.append(s).append(" ");
				}
				
				//envia para o reduce
				try{
					context.write(new Text(sb.toString().trim()+":"+maxk), new IntWritable(1));
				}catch(IOException | InterruptedException e){
					e.printStackTrace();
					System.exit(1);
				}
				
//				itemset[itemsetIndex] = "";
//				return;
			}
			
			if(pt.getPrefixTree().isEmpty() || pt.getPrefixTree().size() <= index || pt.getPrefixTree().get(index) == null){
				itemset[itemsetIndex] = "";
				return;
			}else{
				itemsetIndex++;
				while(i < transaction.length){
					subSet(transaction, pt.getPrefixTree().get(index),i, itemset, itemsetIndex, context);
					i++;
				}
			}
		}
	}
    
    @Override
    public void map(LongWritable key, Text value, Context context){
    	
		//Aplica a função subset e envia o itemset para o reduce
    	StringBuilder sb = new StringBuilder();
    	String[] transaction = value.toString().split(" ");
    	String[] itemset = new String[maxk];
//    	System.out.println("In transaction "+value.toString());
    	if(transaction.length >= mink){
//    		System.out.println("Subset...");
    		for(int i = 0; i < transaction.length; i++){
//    			subset(transaction, prefixTree, 0, sb , context);
    			subSet(transaction, prefixTree, i, itemset, 0, context);
    		}
    	}
    }
    
    /**
     * 
     * @param path
     * @param context
     * @return
     */
    public ArrayList<String> openFile(String path, Context context){
    	fileCached = new ArrayList<String>();
    	try {
			
			Text key = (Text) ReflectionUtils.newInstance(reader.getKeyClass(), context.getConfiguration());
			IntWritable value = (IntWritable) ReflectionUtils.newInstance(reader.getValueClass(), context.getConfiguration());
			
			while (reader.next(key, value)) {
				//System.out.println("Add Key: "+key.toString());
	            fileCached.add(key.toString());
	        }
		} catch (IllegalArgumentException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return fileCached;
    }
}
