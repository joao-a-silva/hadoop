/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package mapred.map;

import java.io.IOException;
import java.net.URI;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Cluster.JobTrackerStatus;
import org.apache.hadoop.mapreduce.FileSystemCounter;
import org.apache.hadoop.mapreduce.Job.JobState;
import org.apache.hadoop.mapreduce.JobACL;
import org.apache.hadoop.mapreduce.JobCounter;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.TaskCounter;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormatCounter;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import app.PrefixTree;


/**
 *
 * @author eduardo
 */
public class Map1 extends Mapper<LongWritable, Text, Text, Text>{
    
    Log log = LogFactory.getLog(Map1.class);
    IntWritable count = new IntWritable(1);
    Text keyOut = new Text();
    double support;
    int totalBlockCount;
    ArrayList<String> candidates;
    ArrayList<String> tempCandidates;
    HashMap<String, Integer> itemSup;
    
    ArrayList<String[]> transactions;
    PrefixTree prefixTree;
    ArrayList<String> blocksIds;
    
    String splitName;
    
    @Override
    public void setup(Context context){
    	String sup = context.getConfiguration().get("support");
    	totalBlockCount = Integer.parseInt(context.getConfiguration().get("totalMaps"));
    	blocksIds = new ArrayList<String>();
    	for(int i = 1; i <= totalBlockCount; i++){
    		blocksIds.add(context.getConfiguration().get("blockId"+i));
    	}
    	
    	support = Double.parseDouble(sup)/100;
    	log.info("Iniciando Map 1...");
    	    	
    }
    @Override
    public void map(LongWritable key, Text value, Context context) throws IOException{
    	//Recebe todo o bloco de transações de uma vez
    	//Aplica-se o algoritmo Apriori
    	System.out.println("\n*****************/////// KEY: "+key);
    	//buildTransactionsArraySet
    	candidates = new ArrayList<String>();
    	prefixTree = new PrefixTree(0);
    	transactions = new ArrayList<String[]>();
    	buildTransactionsArraySet(value.toString());
    	System.out.println("Procentagem do suporte: "+support+"%");
    	support = Math.ceil(support * transactions.size());
    	System.out.println("Valor o suporte: "+support);
    	
    	/*Obtendo um id para o Map*/
    	setSplitName(key);
    	
    	int k = 1;
    	String[] itemset;
    	int itemsetIndex;
    	do{
    		System.out.println("Gerando itens de tamanho "+k);
    		generateCandidates(k, context);
    		//Verificar existência e contar o support de cada itemset
    		
    		System.out.println("Verificando a existência "+candidates.size());
    		if(k > 1){
    			for(String[] transaction: transactions){
    				if(transaction.length >= k){
	    				for(int i = 0; i < transaction.length; i++){
	    					itemset = new String[k];
	    					itemsetIndex = 0;
	    					subSet(transaction, prefixTree, i, k, itemset, itemsetIndex);
	    				}
    				}
    			}
    			
    			/*Remover os itemsets não frequentes*/
    			candidates.removeAll(removeUnFrequentItems(candidates));
    		}
    		
    		k++;
    	}while(candidates.size() > 1);
    	
    	//Envia os elementos da hashMap para o reduce com seu respectivo suporte parcial
    	Set<String> keys = itemSup.keySet();
    	Text keyOut = new Text();
    	Text valueOut = new Text();
    	Integer v;
    	for(String localKeys: keys){
    		v = itemSup.get(localKeys);
    		if(v != null){
    			keyOut.set(localKeys);
    			valueOut.set(String.valueOf(v+":"+splitName));
    			try {
					context.write(keyOut, valueOut);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    	
    }
    
    public void setSplitName(LongWritable offset){
    	
    	splitName = offset+":"+transactions.size();
    	System.out.println("|************************************************************|");
    	System.out.println("Split Name: "+splitName);
    	System.out.println("|************************************************************|");
    	
    }
    
    /**
     * 
     * @param transaction
     * @param pt
     * @param i
     * @param k
     * @param itemset
     * @param itemsetIndex
     */
    private void subSet(String[] transaction, PrefixTree pt, int i,
			int k, String[] itemset, int itemsetIndex) {
    	if(i >= transaction.length){
			return;
		}
		int index = -1;
		
		try{
			index = pt.getPrefix().indexOf(transaction[i]);
		}catch(Exception e){
			e.printStackTrace();
			System.out.println("Prefix: "+pt.getPrefix()+" item: "+transaction[i]);
			System.out.println("Error");
		}
		
		if(index == -1){
			return;
		}else{
			itemset[itemsetIndex] = transaction[i];
			/*if(i == transaction.length-1 && i == k-1){
				StringBuilder sb = new StringBuilder();
				System.out.println("Achou1 "+Arrays.asList(itemset));
				for(String s: itemset){
					if(s != null)
						sb.append(s).append(" ");
				}
				
				itemset[itemsetIndex] = "";
				addToHashItemSup(sb.toString().trim());
				return;
			}else{*/
				i++;
				if(pt.getLevel() == k-1){
					StringBuilder sb = new StringBuilder();
//					System.out.println("Achou2 "+Arrays.asList(itemset));
					for(String s: itemset){
						if(s != null)
							sb.append(s).append(" ");
					}
					addToHashItemSup(sb.toString().trim());
					itemset[itemsetIndex] = "";
					return;
				}
				
				if(pt.getPrefixTree().isEmpty() || pt.getPrefixTree().size() <= index || pt.getPrefixTree().get(index) == null){
					itemset[itemsetIndex] = "";
					return;
				}else{
					itemsetIndex++;
					while(i < transaction.length){
						subSet(transaction, pt.getPrefixTree().get(index),i, k, itemset, itemsetIndex);
						i++;
					}
				}
				
//			}
		}
	}
	public void generateCandidates(int n, Context context){
    	ArrayList<String> tempCandidates = new ArrayList<String>(); 
    	
    	String str1, str2; 
    	StringTokenizer st1, st2;
    	StringBuilder tmpItem;
    	String[] tmpItemsets;
    			
    	if(n==1){
    		itemSup = new HashMap<String, Integer>();
    		for(int i=0; i<transactions.size(); i++){
    			tmpItemsets = transactions.get(i);
    			for(int j = 0; j < tmpItemsets.length; j++){
    				addToHashItemSup(tmpItemsets[j]);
					if(!tempCandidates.contains(tmpItemsets[j])){
						tempCandidates.add(tmpItemsets[j]);		
					}
    			}
    		}
    		
    		System.out.println("Quantidade de itens de tamanho 1: "+tempCandidates.size());
    		tempCandidates.removeAll(removeUnFrequentItems(tempCandidates));
    		Collections.sort(tempCandidates, NUMERICAL_ORDER);
    		
    		//envia para o reduce
    		outputToReduce(tempCandidates, context);
    		
    	}
    	else if(n==2) {
    		itemSup.clear();
    		for(int i=0; i<candidates.size(); i++){
    			tmpItem = new StringBuilder();
    			tmpItem.append(candidates.get(i).trim()).append(" ");
    			for(int j=i+1; j<candidates.size(); j++){
    				tempCandidates.add(tmpItem.toString()+candidates.get(j).trim());
    				
//    				System.out.println("tmpSize in 2: "+tempCandidates.size()+", Adicionando na hash "+tempCandidates.get(tempCandidates.size()-1));
    				prefixTree.add(prefixTree,tempCandidates.get(tempCandidates.size()-1).split(" "),0);
    			}
    		}
    		
    	}else{
    		/*É preciso verificar o prefixo, isso não está sendo feito!!*/
    		String prefix;
    		String sufix;
    		for(int i=0; i<candidates.size(); i++){
//    			System.out.println("Progress: "+context.getProgress());
    			for(int j=i+1; j<candidates.size(); j++){

    				str1 = new String();
    				str2 = new String();
    				
    				prefix = getPrefix(candidates.get(i));
    				
    				if(candidates.get(j).startsWith(prefix)){
    					/*Se o próximo elemento já possui o mesmo prefixo, basta concatenar o sufixo do segundo item.*/
    					sufix = getSufix(candidates.get(j));
    					
						tmpItem = new StringBuilder();
    					tmpItem.append(candidates.get(i)).append(" ").append(sufix);
    					
    					tempCandidates.add(tmpItem.toString().trim());
    		
    					//add to prefixTree
    					//System.out.println("tmpSize in K = "+n+": candidate size "+tempCandidates.size()+", Adicionando na hash "+tempCandidates.get(tempCandidates.size()-1));
    					try{
    						prefixTree.add(prefixTree,tempCandidates.get(tempCandidates.size()-1).split(" "),0);
    					}catch(Exception e){
    						e.printStackTrace();
    					}
    				}
    			}
    		}
    		
    	}
    	candidates.clear();
    	candidates = new ArrayList<String>(tempCandidates);
    	System.out.println("Quantidade de candidatos de tamanho "+n+": "+candidates.size());
    	
    	tempCandidates.clear();
    }
	
	public String getSufix(String kitem){
		String[] spkitem = kitem.split(" ");
		return spkitem[spkitem.length-1].trim();
	}
	
	public String getPrefix(String kitem){
        
        String[] spkitem = kitem.split(" ");
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < spkitem.length-1; i++) {
            
            sb.append(spkitem[i]).append(" ");
        }
        
        //k = spkitem.length;
        return sb.toString();
    }
	
	/**
	 * Envia os itemsets parciais para o reduce
	 * @param arrayCandidates
	 * @param context
	 */
	private void outputToReduce(ArrayList<String> arrayCandidates, Context context) {
		Integer value;
		Text key = new Text();
		Text val = new Text();
		
		for(String item: arrayCandidates){
			value = itemSup.get(item);
			key.set(item);
			val.set(String.valueOf(value)+":"+splitName);
			try {
				context.write(key , val);
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
    
    public void buildTransactionsArraySet(String block){
    	String[] transaction = block.split("\n");
    	
    	for(String tr : transaction){
    		transactions.add(tr.split(" "));    		
    	}
    }
    
    public void addToHashItemSup(String item){
    	Integer value = 0;
    	
    	if((value = itemSup.get(item)) == null){
    		itemSup.put(item, 1);
    	}else{
    		value++;
    		itemSup.put(item, value);
    	}
    }
    
    public ArrayList<String> removeUnFrequentItems(ArrayList<String> tempCandidates){
    	Integer value;
    	ArrayList<String> rmItems = new ArrayList<String>();
    	for(String item: tempCandidates){
    		value = itemSup.get(item);
    		if(value == null || value < support){
    			rmItems.add(item);
    		}
    	}
    	System.out.println("\nRemovendo "+rmItems.size()+" não frequentes.\n");
    	
    	return rmItems;
    }
    
    @Override
    public void cleanup(Context context){
    	context.getConfiguration().set("totalTransactions", String.valueOf(transactions.size()));
    }
    
    private static Comparator<Object> ALPHABETICAL_ORDER = new Comparator<Object>()  {
		public int compare(Object ob1, Object ob2) {
			String name1 = "";
			String name2 = "";
			Collator collator = Collator.getInstance(Locale.getDefault());
	        collator.setStrength(Collator.PRIMARY);
			
			return collator.compare(name1, name2);
		}
	};
	
	private static Comparator<Object> NUMERICAL_ORDER = new Comparator<Object>()  {
		public int compare(Object ob1, Object ob2) {
			int val1 = Integer.parseInt((String)ob1);
			int val2 = Integer.parseInt((String)ob2);
			
			return val1 > val2? 1: val1 < val2? -1 : 0;
			 
		}
	};
}
